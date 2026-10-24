"""Answer generation — gated retrieval + DeepSeek Flash.

Search results favour recall: the UI shows ``k`` candidates and the user picks.
Answers favour precision: one wrong source is enough to make the answer wrong,
so retrieval here is much narrower.

Three gates, in order:

1. **Title fast path** — when the normalised query names a work in the library,
   answer from that work only; the vector branch is not consulted.
2. **Keyword gate** — otherwise keep the FTS hits within ``_FTS_RATIO`` of the
   best BM25 score.  For short entity/topic queries this is the only signal that
   actually separates relevant books: whole-book embeddings sit around 0.6
   cosine for almost everything, so a similarity threshold cannot be used.
3. **Vector fallback** — only when FTS found nothing, keep vectors above
   ``_MIN_VECTOR_SIMILARITY``.  A query with no lexical overlap and no strong
   vector match yields no candidates, and the caller answers "not in the
   library" without spending a model call.

The model must cite ``[book_id=N]`` for ids inside the candidate set; an answer
with ``NONE``, no citation, or any citation outside the set is discarded and
reported as "not in the library" — the cheapest guard against answering from
its own knowledge.
"""

import logging
import os
import re
import time
from dataclasses import dataclass
from typing import Optional

import httpx
from sqlalchemy import text
from sqlalchemy.orm import Session

from .vector_store import normalize_query, search_fts, search_vectors

logger = logging.getLogger(__name__)

ANSWER_MODEL = "deepseek-flash"
_ANSWER_URL = "https://api.deepseek.com/chat/completions"
_ANSWER_TIMEOUT = 60.0
_ANSWER_ATTEMPTS = 2

MAX_CANDIDATES = 5
# FTS hits below this fraction of the best BM25 score are the long tail.
_FTS_RATIO = 0.5
# Whole-book embeddings score ~0.6 cosine even for unrelated books with this
# model, so only clearly similar vectors are added to the candidate set.
_MIN_VECTOR_SIMILARITY = 0.58
# Query→title containment needs at least this many characters: an author name
# ("刘慈欣") must not collapse a topic question into the books whose title
# happens to contain it.
_TITLE_CONTAINMENT_MIN = 4
# Characters of summary/introduction per candidate in the prompt.
_EXCERPT_CHARS = 320

_CITE_RE = re.compile(r"\[book_id\s*=\s*(\d+)\]")
_NON_WORD_RE = re.compile(r"[\W_]+")
_NONE_RE = re.compile(r"^none\s*[.。]?$", re.IGNORECASE)

NO_ANSWER_TEXT = "未在书库中找到相关书籍。"

# Why an answer came back empty; used in logs and by the evaluation harness.
REASON_NO_CANDIDATES = "no_candidates"
REASON_MODEL_NONE = "model_none"
REASON_NO_CITATION = "no_citation"
REASON_INVALID_CITATION = "invalid_citation"


class AnswerUnavailable(RuntimeError):
    """The answer could not be produced (no key, or the model call failed)."""


@dataclass
class AnswerResult:
    query: str
    answer: str
    sources: list[dict]
    no_answer: bool
    reason: str = ""


def title_key(value: str) -> str:
    """Normalise a title/query for loose comparison."""
    return _NON_WORD_RE.sub("", (value or "").lower())


def title_matches(expected: str, actual: str) -> bool:
    """Loose work-name match: "三国演义" matches "毛宗岗批评本·三国演义"."""
    expected_key, actual_key = title_key(expected), title_key(actual)
    if not expected_key or not actual_key:
        return False
    return (
        expected_key == actual_key
        or expected_key in actual_key
        or actual_key in expected_key
    )


def _title_match_ids(db: Session, query: str) -> list[int]:
    """Book ids whose title *is* the query (or an edition of it).

    Both the raw and the normalised query are tried: normalisation strips a
    trailing "的书", which would otherwise mangle a title ending the same way
    ("有用的书" → "有用").
    """
    keys = {
        key
        for key in (title_key(query), title_key(normalize_query(query)))
        if len(key) >= 2
    }
    if not keys:
        return []

    hits: list[int] = []
    for bid, title, title_cn in db.execute(
        text("SELECT id, title, title_cn FROM books")
    ):
        for value in (title_cn, title):
            candidate = title_key(value or "")
            if len(candidate) < 2:
                continue
            matched = candidate in keys
            if not matched:
                matched = len(candidate) >= 3 and any(
                    candidate in key for key in keys
                )
            if not matched:
                # A query fragment of an edition-prefixed title ("三国演义" →
                # "毛宗岗批评本·三国演义") is a title match; a shorter topic or
                # author name is not.
                matched = any(
                    len(key) >= _TITLE_CONTAINMENT_MIN and key in candidate
                    for key in keys
                )
            if matched:
                hits.append(bid)
                break
    return hits[:3]


def _load_candidates(db: Session, ids: list[int]) -> list[dict]:
    """Book metadata for the prompt, in the given id order."""
    if not ids:
        return []
    placeholders = ", ".join(f":id{i}" for i in range(len(ids)))
    params = {f"id{i}": bid for i, bid in enumerate(ids)}
    rows = db.execute(
        text(
            "SELECT id, title, title_cn, summary, introduction FROM books "
            f"WHERE id IN ({placeholders})"
        ),
        params,
    ).fetchall()
    by_id = {
        row[0]: {
            "book_id": row[0],
            "title": row[1] or "",
            "title_cn": row[2] or "",
            "summary": row[3] or "",
            "introduction": row[4] or "",
        }
        for row in rows
    }

    if by_id:
        author_rows = db.execute(
            text(
                "SELECT ba.book_id, a.name, a.name_cn FROM book_authors ba "
                "JOIN authors a ON a.id = ba.author_id"
            )
        ).fetchall()
        authors: dict[int, list[str]] = {}
        for book_id, name, name_cn in author_rows:
            authors.setdefault(book_id, []).append(name_cn or name)
    else:
        authors = {}

    return [
        {**by_id[bid], "authors": authors.get(bid, [])}
        for bid in ids
        if bid in by_id
    ]


def select_candidates(
    fts_hits: list[dict],
    vector_hits: list[dict],
    top_k: int = MAX_CANDIDATES,
) -> list[int]:
    """Pick book ids from the real retrieval signals.

    Keyword hits lead (they matched the query terms); clearly similar vectors
    follow, because a book *about* a topic may never use the query's exact
    words.  The absolute vector floor is what keeps the long tail out.
    """
    ids: list[int] = []
    if fts_hits:
        best = fts_hits[0]["score"] or 0.0
        ids = [
            r["book_id"]
            for r in fts_hits
            if best and r["score"] >= best * _FTS_RATIO
        ]

    # Keyword hits lead, but they only cover books that literally contain the
    # terms: a book *about* "天文观测" may never use that phrase.  Add clearly
    # similar vectors after them so topical questions keep their expected books.
    seen = set(ids)
    for result in vector_hits:
        if result["score"] >= _MIN_VECTOR_SIMILARITY and result["book_id"] not in seen:
            ids.append(result["book_id"])
            seen.add(result["book_id"])

    return ids[:top_k]


def retrieve_for_answer(
    db: Session, query: str, top_k: int = MAX_CANDIDATES
) -> list[dict]:
    """The narrow candidate set one answer may be built from."""
    top_k = max(1, min(int(top_k), MAX_CANDIDATES))

    title_ids = _title_match_ids(db, query)
    if title_ids:
        return _load_candidates(db, title_ids)

    fts_hits = search_fts(db, query, top_k=max(top_k * 3, 9))
    vector_hits = search_vectors(db, query, top_k=max(top_k * 3, 9))
    return _load_candidates(db, select_candidates(fts_hits, vector_hits, top_k))


def _excerpt(candidate: dict) -> str:
    body = (candidate["summary"] or "").strip() or (
        candidate["introduction"] or ""
    ).strip()
    body = re.sub(r"\s+", " ", body)
    if len(body) > _EXCERPT_CHARS:
        body = body[:_EXCERPT_CHARS].rstrip() + "..."
    return body


def build_prompt(query: str, candidates: list[dict]) -> str:
    """Prompt with explicit rules and citation format."""
    lines = []
    for candidate in candidates:
        title = candidate["title_cn"] or candidate["title"]
        authors = "; ".join(candidate["authors"]) or "未知"
        lines.append(
            f"[book_id={candidate['book_id']}] 书名：{title}｜作者：{authors}"
            f"｜内容：{_excerpt(candidate)}"
        )
    return (
        "你是图书馆的检索助手。下面是从用户藏书库中检索到的候选书籍。\n"
        "请只根据这些书籍的信息回答用户问题。\n\n"
        "规则：\n"
        "1. 只能使用候选书籍里的信息，不要使用候选之外的知识，不要编造情节或观点。\n"
        "2. 如果候选书籍都无法回答问题，或问题需要实时/外部信息"
        "（天气、股市行情、比分、新闻、路线等书籍无法提供的内容），只输出 NONE。\n"
        "3. 回答用中文，2-5 句话，简洁准确。\n"
        "4. 在回答末尾用 [book_id=编号] 标注用到的书，可以标注多个。\n\n"
        f"用户问题：{query}\n\n"
        "候选书籍：\n" + "\n".join(lines)
    )


ANSWER_SYSTEM = (
    "你是中文图书检索助手。只输出答案正文，不要标题或解释；"
    "结束时用 [book_id=编号] 标注引用。"
)


def deepseek_chat(
    prompt: str,
    *,
    system: str,
    max_tokens: int = 512,
    temperature: float = 0.3,
) -> Optional[str]:
    """One DeepSeek Flash chat completion; None on missing key or failure."""
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        return None

    payload = {
        "model": ANSWER_MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        "thinking": {"type": "disabled"},
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": False,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    for attempt in range(_ANSWER_ATTEMPTS):
        try:
            response = httpx.post(
                _ANSWER_URL,
                json=payload,
                headers=headers,
                timeout=_ANSWER_TIMEOUT,
            )
            if response.status_code == 429 or response.status_code >= 500:
                raise RuntimeError(f"HTTP {response.status_code}")
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return (content or "").strip()
        except Exception as exc:
            logger.warning(
                "DeepSeek answer failed (attempt %s/%s): %s",
                attempt + 1,
                _ANSWER_ATTEMPTS,
                exc,
            )
            if attempt == 0:
                time.sleep(2)

    return None


def _no_answer(query: str, reason: str) -> AnswerResult:
    logger.info("rag answer: %s for %r", reason, query)
    return AnswerResult(query, NO_ANSWER_TEXT, [], True, reason)


def answer_question(
    db: Session, query: str, top_k: int = MAX_CANDIDATES
) -> AnswerResult:
    """Retrieve, ask the model, and validate its citations."""
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        raise AnswerUnavailable("DEEPSEEK_API_KEY is not configured")

    candidates = retrieve_for_answer(db, query, top_k)
    if not candidates:
        return _no_answer(query, REASON_NO_CANDIDATES)

    raw = deepseek_chat(
        build_prompt(query, candidates),
        system=ANSWER_SYSTEM,
        max_tokens=512,
        temperature=0.3,
    )
    if raw is None:
        raise AnswerUnavailable("DeepSeek answer request failed")

    body = raw.strip()
    # "NONE" may arrive with a citation attached ("NONE [book_id=3]"): strip the
    # markers before the sentinel check so it cannot slip through as an answer.
    if _NONE_RE.match(_CITE_RE.sub("", body).strip()):
        return _no_answer(query, REASON_MODEL_NONE)

    cited = [int(match) for match in _CITE_RE.findall(body)]
    if not cited:
        # No citation: the model most likely answered from its own knowledge,
        # which is exactly what this endpoint must not do.
        return _no_answer(query, REASON_NO_CITATION)

    candidate_ids = {c["book_id"] for c in candidates}
    if any(bid not in candidate_ids for bid in cited):
        # One unknown id taints the whole answer: we cannot tell which claims
        # came from which book, so it is not trustworthy enough to serve.
        logger.info("rag answer: invalid citation for %r (cited=%s)", query, cited)
        return _no_answer(query, REASON_INVALID_CITATION)

    answer = _CITE_RE.sub("", body).strip()
    if not answer:
        return _no_answer(query, REASON_NO_CITATION)

    by_id = {c["book_id"]: c for c in candidates}
    sources = [
        {
            "book_id": bid,
            "title": by_id[bid]["title"],
            "title_cn": by_id[bid]["title_cn"],
            "authors": by_id[bid]["authors"],
        }
        for bid in dict.fromkeys(cited)
    ]
    return AnswerResult(query, answer, sources, False, "")
