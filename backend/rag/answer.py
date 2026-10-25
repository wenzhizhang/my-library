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

import json
import logging
import os
import re
from dataclasses import asdict, dataclass, field, replace
from typing import Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

from .intent import (
    _INTENT_NOTES,
    _INTENT_RESOLVERS,
    _author_ids_for_name,
    _author_match_ids,
    _exact_author_query,
    _series_ids_for_name,
    _series_match_ids,
    parse_intent,
)
from .llm import AnswerUnavailable, deepseek_chat
from .text import title_key, title_matches
from .vector_store import normalize_query, search_fts, search_vectors

logger = logging.getLogger(__name__)

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
# "沙丘系列有哪些" is a series question, not the single book "沙丘".
_SERIES_HINTS = ("系列", "套装", "全集", "合集", "丛书", "续集", "后续")
# A comparison ("《沙丘》和《三体》") may fast-path two-character titles; a plain
# long question that happens to contain one must not.
_CONJUNCTIONS = ("和", "与", "、", "及", "跟")
# "三体的作者" wraps a short title in a few characters and is still about that
# book; "我想了解一下沙丘" wraps it in a whole sentence and is not.
_SHORT_TITLE_WRAP = 4
# Characters of summary/introduction per candidate in the prompt. A single
# candidate (a quoted book, or the one work a question names) gets more room.
_EXCERPT_CHARS = 320
_SINGLE_EXCERPT_CHARS = 1200
# Questions that ask to relate several works; the prompt then demands a
# citation for each one, because the model tends to cite only the first.
_COMPARISON_MARKERS = ("区别", "不同", "比较", "对比", "关系", "哪个好")

# How many books the assistant may page through for one question. A user with
# 38 editions of 红楼梦 must be able to keep asking "还有么" and get new ones.
_POOL_LIMIT = 40

# "Show me more" phrasings. The deterministic list covers the common Chinese
# and English forms; anything else that still smells like a continuation goes
# through one tiny Flash classification so unseen paraphrases are understood.
_MORE_CUES_ZH = (
    "还有", "更多", "再来", "再给", "再列", "再几", "多来", "多列", "换一批",
    "其他版本", "其它版本", "别的版本", "别本", "其它的", "继续", "全部",
    "所有版本", "几本", "几种", "几个",
)
_MORE_CUES_EN = ("more", "others", "other versions", "next", "another", "show more")
_MORE_HINTS = ("还", "再", "多", "别", "其他", "其它", "继续", "几", "more", "other")
# Conversation context.  The rewriter needs more turns than the answer prompt
# repeats, but both are bounded so a long chat cannot bloat the payload.
_REWRITE_HISTORY_TURNS = 8
_PROMPT_HISTORY_TURNS = 4
# Keep in sync with the prompt's "不超过 40 字" instruction.
_REWRITE_MAX_CHARS = 40

_CITE_RE = re.compile(r"\[book_id\s*=\s*(\d+)\]")
_NON_WORD_RE = re.compile(r"[\W_]+")
_NONE_RE = re.compile(r"^none\s*[.。]?$", re.IGNORECASE)

NO_ANSWER_TEXT = "未在书库中找到相关书籍。"

# Why an answer came back empty; used in logs and by the evaluation harness.
REASON_NO_CANDIDATES = "no_candidates"
REASON_MODEL_NONE = "model_none"
REASON_NO_CITATION = "no_citation"
REASON_INVALID_CITATION = "invalid_citation"
REASON_NO_MORE = "no_more"


class BookNotFound(LookupError):
    """The quoted book does not exist in the caller's library."""


@dataclass
class Candidate:
    """One book row, as the retrieval/prompt code needs it."""
    book_id: int
    title: str = ""
    title_cn: str = ""
    summary: str = ""
    introduction: str = ""
    thumb_image: str = ""
    publish_date: str = ""
    edition: str = ""
    printing_info: str = ""
    isbn: str = ""
    pages: str = ""
    price: str = ""
    book_count: str = ""
    binding_type: str = ""
    language: str = ""
    douban_score: str = ""
    tags: str = ""
    publisher: str = ""
    category: str = ""
    series: str = ""
    authors: list[str] = field(default_factory=list)

    def __getitem__(self, key: str):
        return getattr(self, key)

    def get(self, key: str, default=None):
        return getattr(self, key, default)

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class AnswerResult:
    query: str
    answer: str
    sources: list[dict]
    no_answer: bool
    reason: str = ""
    rewritten_query: str = ""
    candidates: list[dict] = field(default_factory=list)
    more: list[dict] = field(default_factory=list)
    has_more: bool = False
    total_candidates: int = 0
    total_titles: int = 0
    intent: dict = field(default_factory=dict)


def _title_match_ids(
    db: Session, query: str, limit: int = MAX_CANDIDATES
) -> list[int]:
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

    if any(hint in (query or "") for hint in _SERIES_HINTS):
        # A series question is answered from several volumes, not one title.
        return []
    allow_short = any(
        marker in normalize_query(query) for marker in _CONJUNCTIONS
    )

    rows = db.execute(text("SELECT id, title, title_cn FROM books")).fetchall()
    all_keys = {
        candidate
        for _, title, title_cn in rows
        for value in (title_cn, title)
        for candidate in (title_key(value or ""),)
        if len(candidate) >= 2
    }
    # A key that is itself a title ("红楼梦") names a work, so every edition
    # whose title contains it qualifies. The same applies when the query wraps
    # a known title ("红楼梦有哪些版本"); short titles need a short query so a
    # passing mention does not collapse a topic question into one work.
    work_keys = {candidate for candidate in all_keys if candidate in keys}
    for key in keys:
        for candidate in all_keys:
            if candidate in key and (
                len(candidate) >= 3 or len(key) <= 16
            ):
                work_keys.add(candidate)

    # Group matches by the work key that matched, so a query naming several
    # works yields at least one edition of each before duplicate editions —
    # otherwise 5 copies of one work could crowd the compared work out.
    hits: list[tuple[int, str]] = []
    for bid, title, title_cn in rows:
        for value in (title_cn, title):
            candidate = title_key(value or "")
            if len(candidate) < 2:
                continue
            matched_key = candidate if candidate in keys else None
            if not matched_key:
                # The query names this title inside a longer question
                # ("《沙丘》和《三体》有什么区别", "三体的作者"). A one/two
                # character title only qualifies when the wrapper is short or
                # the question compares several works — never for a sentence
                # that merely mentions the characters.
                for key in keys:
                    if (
                        candidate in key
                        and len(key) > len(candidate)
                        and (
                            len(candidate) >= 3
                            or allow_short
                            or len(key) - len(candidate) <= _SHORT_TITLE_WRAP
                        )
                    ):
                        matched_key = key
                        break
            if not matched_key:
                # A query fragment of an edition-prefixed title ("三国演义" →
                # "毛宗岗批评本·三国演义"), or any edition of a named work
                # ("红楼梦" → all 38 editions).
                for key in keys | work_keys:
                    if key in candidate and (
                        len(key) >= _TITLE_CONTAINMENT_MIN or key in work_keys
                    ):
                        matched_key = key
                        break
            if matched_key:
                hits.append((bid, matched_key))
                break

    ordered = _first_per_key(hits, lambda hit: hit[1])
    return [bid for bid, _ in ordered][:limit]



_CANDIDATE_FIELDS = (
    "book_id", "title", "title_cn", "summary", "introduction", "thumb_image",
    "publish_date", "edition", "printing_info", "isbn", "pages", "price",
    "book_count", "binding_type", "language", "douban_score", "tags",
    "publisher", "category", "series",
)


def _load_candidates(db: Session, ids: list[int]) -> list[Candidate]:
    """Book metadata for the prompt, in the given id order."""
    if not ids:
        return []
    placeholders = ", ".join(f":id{i}" for i in range(len(ids)))
    params = {f"id{i}": bid for i, bid in enumerate(ids)}
    rows = db.execute(
        text(
            "SELECT b.id, b.title, b.title_cn, b.summary, b.introduction, "
            "b.thumb_image, b.publish_date, b.edition, b.printing_info, "
            "b.isbn, b.pages, b.price, b.book_count, b.binding_type, "
            "b.language, b.douban_score, b.tags, p.name, c.name, s.name "
            "FROM books b "
            "LEFT JOIN publishers p ON p.id = b.publisher_id "
            "LEFT JOIN categories c ON c.id = b.category_id "
            "LEFT JOIN book_series s ON s.id = b.book_series_id "
            f"WHERE b.id IN ({placeholders})"
        ),
        params,
    ).fetchall()
    by_id: dict[int, Candidate] = {}
    for row in rows:
        values = list(row)
        values[6] = str(row[6])[:10] if row[6] else ""
        candidate = Candidate(**dict(zip(_CANDIDATE_FIELDS, values)))
        by_id[candidate.book_id] = candidate

    authors: dict[int, list[str]] = {}
    if by_id:
        author_rows = db.execute(
            text(
                "SELECT ba.book_id, a.name, a.name_cn FROM book_authors ba "
                "JOIN authors a ON a.id = ba.author_id"
            )
        ).fetchall()
        for book_id, name, name_cn in author_rows:
            authors.setdefault(book_id, []).append(name_cn or name)

    return [
        replace(by_id[bid], authors=authors.get(bid, []))
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


def _first_per_key(items, key) -> list:
    """Keep the first item of each key, then append the rest in order."""
    first, rest, seen = [], [], set()
    for item in items:
        item_key = key(item)
        if item_key in seen:
            rest.append(item)
        else:
            seen.add(item_key)
            first.append(item)
    return first + rest


_EDITION_SUFFIX_RE = re.compile(
    r"(?:\d+周年|纪念版|珍藏版|收藏版|套装|全集|修订版|增订版|插图版|"
    r"精装版|平装版|新版|第[一二三四五六七八九十\d]+版)+$"
)


def _work_key(title: str) -> str:
    """Title key with edition suffixes removed ("魔戒50周年纪念版" → "魔戒")."""
    key = title_key(title)
    while True:
        stripped = _EDITION_SUFFIX_RE.sub("", key)
        if stripped == key:
            return key
        key = stripped


def _distinct_titles_first(candidates: list[dict]) -> list[dict]:
    """One edition per title before duplicates, so 2 editions of one work
    cannot crowd the other works out of the five answer slots."""
    return _first_per_key(
        candidates,
        lambda candidate: _work_key(candidate["title_cn"] or candidate["title"]),
    )


def retrieve_candidates(
    db: Session,
    query: str,
    limit: int = _POOL_LIMIT,
    intent: Optional[dict] = None,
) -> list[dict]:
    """The ordered candidate pool for a query (title matches or hybrid hits)."""
    limit = max(1, int(limit))

    # The parsed intent names an entity ("托尔金写过哪些书"): resolve it
    # directly before any literal matching.
    if intent:
        kind, value = intent.get("kind"), (intent.get("value") or "").strip()
        resolver = _INTENT_RESOLVERS.get(kind)
        if resolver and value:
            ids = resolver(db, value, limit)
            if ids:
                return _distinct_titles_first(_load_candidates(db, ids))
        # A title intent is only safe for a single work: a comparison needs
        # several, and the multi-title heuristic already handles that.
        if kind == "title" and value and not any(
            marker in query for marker in (*_CONJUNCTIONS, *_COMPARISON_MARKERS)
        ):
            ids = _title_match_ids(db, value, limit)
            if ids:
                return _distinct_titles_first(_load_candidates(db, ids))

    # "苏轼的书" / "托尔金著的书" name a person: their works must win over a
    # title that merely contains the name ("苏轼" the art book).
    author_ids = _author_match_ids(db, query, limit)
    if author_ids and ("著" in query or _exact_author_query(db, query)):
        return _distinct_titles_first(_load_candidates(db, author_ids))

    # A title query must keep its book even when the title contains a series
    # name ("点校本二十四史修订本：史记" is one volume, not the whole series).
    title_ids = _title_match_ids(db, query, limit)
    if title_ids:
        return _distinct_titles_first(_load_candidates(db, title_ids))

    series_ids = _series_match_ids(db, query, limit)
    if series_ids:
        return _distinct_titles_first(_load_candidates(db, series_ids))

    fetch = max(limit * 3, 9)
    fts_hits = search_fts(db, query, top_k=fetch)
    vector_hits = search_vectors(db, query, top_k=fetch)
    return _distinct_titles_first(
        _load_candidates(db, select_candidates(fts_hits, vector_hits, limit))
    )


def _tags(value: str) -> str:
    try:
        parsed = json.loads(value or "[]")
    except ValueError:
        return ""
    if not isinstance(parsed, list):
        return ""
    return "、".join(str(tag) for tag in parsed if tag)


def _book_meta(candidate: dict) -> str:
    """Compact publication facts, so "哪年出版的" is answerable."""
    fields = (
        ("出版日期", candidate.get("publish_date")),
        ("出版社", candidate.get("publisher")),
        ("版次", candidate.get("edition")),
        ("印次", candidate.get("printing_info")),
        ("ISBN", candidate.get("isbn")),
        ("页数", candidate.get("pages")),
        ("装帧", candidate.get("binding_type")),
        ("语言", candidate.get("language")),
        ("定价", candidate.get("price")),
        ("册数", candidate.get("book_count")),
        ("系列", candidate.get("series")),
        ("分类", candidate.get("category")),
        ("豆瓣评分", candidate.get("douban_score")),
        ("标签", _tags(candidate.get("tags", ""))),
    )
    return "｜".join(f"{label}：{value}" for label, value in fields if value)


def _excerpt(candidate: dict, limit: int = _EXCERPT_CHARS) -> str:
    body = (candidate["summary"] or "").strip() or (
        candidate["introduction"] or ""
    ).strip()
    body = re.sub(r"\s+", " ", body)
    if len(body) > limit:
        body = body[:limit].rstrip() + "..."
    return body


def _normalise_turns(history: Optional[list[dict]]) -> list[dict]:
    """Coerce request turns into ``{"role", "content"}`` once, at the boundary."""
    turns = []
    for turn in history or []:
        if isinstance(turn, dict):
            role, content = turn.get("role"), turn.get("content", "")
        else:
            role, content = getattr(turn, "role", ""), getattr(turn, "content", "")
        content = re.sub(r"\s+", " ", str(content or "")).strip()
        if content:
            turns.append(
                {"role": "user" if role == "user" else "assistant", "content": content}
            )
    return turns


def _transcript(turns: list[dict], limit: int = _REWRITE_HISTORY_TURNS) -> str:
    """Flat ``用户：…/助手：…`` transcript, newest ``limit`` turns."""
    return "\n".join(
        f"{'用户' if turn['role'] == 'user' else '助手'}：{turn['content'][:200]}"
        for turn in turns[-limit:]
    )


def _compact(text: str) -> str:
    return re.sub(r"[\s，。！？、,.!?~～…:：;；\"'“”‘’]+", "", text or "").lower()


def _strip_more_cues(text: str) -> str:
    compact = _compact(text)
    for cue in sorted((*_MORE_CUES_ZH, *_MORE_CUES_EN), key=len, reverse=True):
        compact = compact.replace(cue, "")
    return compact


def _looks_like_more(text: str) -> bool:
    compact = _compact(text)
    if not compact or len(compact) > 40:
        return False
    return any(cue in compact for cue in (*_MORE_CUES_ZH, *_MORE_CUES_EN))


def _classify_more(query: str, turns: list[dict]) -> bool:
    """One tiny Flash call for continuations the cue list does not know."""
    transcript = _transcript(turns, limit=4)
    raw = deepseek_chat(
        f"对话历史：\n{transcript}\n\n用户最新消息：{query}\n\n"
        "用户最新消息是否在要求继续查看更多的书"
        "（例如“还有吗”“再来几个”“其他版本”）？只回答 YES 或 NO。",
        system="你是意图分类器，只输出 YES 或 NO。",
        max_tokens=4,
        temperature=0.0,
    )
    return bool(raw) and raw.strip().upper().startswith("Y")


def _is_more_request(query: str, turns: list[dict]) -> bool:
    if not turns:
        return False
    if _looks_like_more(query):
        return True
    compact = _compact(query)
    if len(compact) <= 30 and any(hint in compact for hint in _MORE_HINTS):
        return _classify_more(query, turns)
    return False


def _last_substantive_question(turns: list[dict]) -> str:
    for turn in reversed(turns):
        if turn["role"] == "user" and len(_strip_more_cues(turn["content"])) >= 4:
            return turn["content"]
    return ""


def _more_context(query: str, turns: list[dict]) -> str:
    """Use the follow-up's own topic when it names one, else the last question."""
    if len(_strip_more_cues(query)) >= 4:
        return query
    return _last_substantive_question(turns) or query


def _history_block(turns: list[dict]) -> str:
    """Recent turns, trimmed, for the answer prompt."""
    return _transcript(turns, limit=_PROMPT_HISTORY_TURNS)


def build_prompt(
    query: str,
    candidates: list[dict],
    history: Optional[list[dict]] = None,
    total_candidates: Optional[int] = None,
    total_titles: Optional[int] = None,
    intent: Optional[dict] = None,
) -> str:
    """Prompt with explicit rules and citation format."""
    history_text = _history_block(_normalise_turns(history))
    history_section = f"对话历史：\n{history_text}\n\n" if history_text else ""
    excerpt_chars = (
        _SINGLE_EXCERPT_CHARS if len(candidates) == 1 else _EXCERPT_CHARS
    )
    lines = []
    for candidate in candidates:
        title = candidate["title_cn"] or candidate["title"]
        authors = "; ".join(candidate["authors"]) or "未知"
        meta = _book_meta(candidate)
        lines.append(
            f"[book_id={candidate['book_id']}] 书名：{title}｜作者：{authors}"
            + (f"｜{meta}" if meta else "")
            + f"｜内容：{_excerpt(candidate, excerpt_chars)}"
        )
    distinct_titles = {
        candidate["title_cn"] or candidate["title"] for candidate in candidates
    }
    intent_note = ""
    if intent and intent.get("value"):
        note = _INTENT_NOTES.get(intent.get("kind", ""))
        if note:
            intent_note = note.format(value=intent["value"]) + "\n"

    shown_titles = len(
        {_work_key(candidate["title_cn"] or candidate["title"]) for candidate in candidates}
    )
    omitted_titles = max(0, (total_titles or 0) - shown_titles)
    subset_note = (
        f"注意：与问题相关的书共 {total_candidates or 0} 本（{total_titles or 0} 种），"
        f"这里只列出前 {len(candidates)} 本（{shown_titles} 种）；"
        f"如果用户问有哪些书，请说明还有 {omitted_titles} 种没有列出。\n"
        if omitted_titles
        else ""
    )
    emphasis = (
        "本题涉及多本书，回答时必须分别引用每一本书的 [book_id=...]。\n"
        if len(distinct_titles) >= 2
        and any(marker in query for marker in _COMPARISON_MARKERS)
        else ""
    )
    return (
        "你是图书馆的检索助手。下面是从用户藏书库中检索到的候选书籍。\n"
        "请只根据这些书籍的信息回答用户问题。\n\n"
        "规则：\n"
        "1. 只能使用候选书籍里的信息，不要使用候选之外的知识，不要编造情节或观点。\n"
        "2. 如果候选书籍都无法回答问题，或问题需要实时/外部信息"
        "（天气、股市行情、比分、新闻、路线等书籍无法提供的内容），只输出 NONE。\n"
        "3. 回答用中文，2-5 句话，简洁准确。\n"
        "4. 在回答末尾用 [book_id=编号] 标注用到的书，可以标注多个。\n"
        "5. 如果提供了对话历史，结合历史理解当前问题（例如“作者是谁”指上一条讨论的书）。\n"
        "6. 如果问题要求比较多本书，必须分别引用相关的每一本书，不能只引用其中一本。\n"
        "7. 候选信息里的出版日期、出版社、ISBN、页数等出版信息可以直接用于回答。\n\n"
        f"{intent_note}"
        f"{emphasis}"
        f"{subset_note}"
        f"{history_section}"
        f"用户问题：{query}\n\n"
        "候选书籍：\n" + "\n".join(lines)
    )


ANSWER_SYSTEM = (
    "你是中文图书检索助手。只输出答案正文，不要标题或解释；"
    "结束时用 [book_id=编号] 标注引用。"
)


def resolve_followup_query(query: str, history: Optional[list[dict]]) -> str:
    """Turn a follow-up ("作者是谁") into a standalone retrieval query.

    Multi-turn questions usually omit the subject; retrieving on the raw
    follow-up finds nothing useful.  One cheap Flash call rewrites it against
    the recent turns; any failure or suspicious output falls back to the
    original query, so multi-turn never becomes less reliable than single-turn.
    """
    turns = _normalise_turns(history)
    if not turns:
        return query
    transcript = _transcript(turns)

    rewritten = deepseek_chat(
        "把用户最新的问题改写成不依赖上下文、可以单独检索的查询。\n"
        "只输出改写后的查询，不要解释；已经是独立问题就原样输出。\n"
        "保留原问题里的书名、作者等专有名词，不要添加原问题没有的限定词"
        "（电影、电视剧、年份、版本等）。\n\n"
        f"对话历史：\n{transcript}\n\n最新问题：{query}",
        system="你是检索查询改写器。只输出改写后的查询，不超过 40 字。",
        max_tokens=64,
        temperature=0.0,
    )
    if not rewritten:
        return query
    rewritten = rewritten.strip().strip("《》“”\"'").strip()
    rewritten = rewritten.replace("《", "").replace("》", "").strip()
    if not rewritten or len(rewritten) > _REWRITE_MAX_CHARS:
        return query
    logger.info("rag answer: follow-up %r rewritten to %r", query, rewritten)
    return rewritten


def _answer_more(
    db: Session,
    query: str,
    turns: list[dict],
    seen_ids: list[int],
    top_k: int,
) -> AnswerResult:
    """Page through the candidate pool for "还有么"/"more" follow-ups."""
    context = _more_context(query, turns)
    pool = retrieve_candidates(
        db, context, limit=_POOL_LIMIT, intent=parse_intent(context)
    )
    seen = set(seen_ids or ())
    fresh = [candidate for candidate in pool if candidate["book_id"] not in seen]
    if not fresh:
        return _no_answer(query, REASON_NO_MORE)

    limit = max(1, min(int(top_k), MAX_CANDIDATES))
    page = fresh[:limit]
    return AnswerResult(
        query=query,
        answer="还有这些：",
        sources=[],
        no_answer=False,
        more=[candidate.to_dict() for candidate in page],
        has_more=len(fresh) > len(page),
    )


def _no_answer(
    query: str,
    reason: str,
    *,
    candidates: Optional[list[dict]] = None,
    rewritten_query: str = "",
) -> AnswerResult:
    logger.info("rag answer: %s for %r", reason, query)
    return AnswerResult(
        query=query,
        answer=NO_ANSWER_TEXT,
        sources=[],
        no_answer=True,
        reason=reason,
        rewritten_query=rewritten_query,
        candidates=candidates or [],
    )


def answer_question(
    db: Session,
    query: str,
    top_k: int = MAX_CANDIDATES,
    history: Optional[list[dict]] = None,
    seen_book_ids: Optional[list[int]] = None,
    focus_book_id: Optional[int] = None,
) -> AnswerResult:
    """Retrieve, ask the model, and validate its citations."""
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        raise AnswerUnavailable("DEEPSEEK_API_KEY is not configured")

    turns = _normalise_turns(history)
    intent: dict = {}
    if focus_book_id is not None:
        # The user quoted a book: answer from it, before any "more" handling —
        # the quote must never be silently dropped.
        pool = _load_candidates(db, [int(focus_book_id)])
        if not pool:
            raise BookNotFound(f"book {focus_book_id} not found")
        effective_query = query
        rewritten = ""
        candidates = pool
    elif turns and _is_more_request(query, turns):
        return _answer_more(db, query, turns, seen_book_ids or [], top_k)
    else:
        effective_query = resolve_followup_query(query, turns) if turns else query
        rewritten = effective_query if effective_query != query else ""
        intent = parse_intent(effective_query)
        pool = retrieve_candidates(
            db, effective_query, limit=max(_POOL_LIMIT, top_k), intent=intent
        )
        candidates = pool[: max(1, min(int(top_k), MAX_CANDIDATES))]
    if not candidates:
        return _no_answer(
            query, REASON_NO_CANDIDATES, rewritten_query=rewritten
        )

    raw = deepseek_chat(
        build_prompt(
            query,
            candidates,
            turns,
            total_candidates=len(pool),
            total_titles=len(
                {_work_key(c["title_cn"] or c["title"]) for c in pool}
            ),
            intent=intent,
        ),
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
        return _no_answer(
            query, REASON_MODEL_NONE, candidates=candidates, rewritten_query=rewritten
        )

    cited = [int(match) for match in _CITE_RE.findall(body)]
    if not cited:
        # No citation: the model most likely answered from its own knowledge,
        # which is exactly what this endpoint must not do.
        return _no_answer(
            query, REASON_NO_CITATION, candidates=candidates, rewritten_query=rewritten
        )

    candidate_ids = {c["book_id"] for c in candidates}
    if any(bid not in candidate_ids for bid in cited):
        # One unknown id taints the whole answer: we cannot tell which claims
        # came from which book, so it is not trustworthy enough to serve.
        logger.info("rag answer: invalid citation for %r (cited=%s)", query, cited)
        return _no_answer(
            query, REASON_INVALID_CITATION, candidates=candidates, rewritten_query=rewritten
        )

    answer = _CITE_RE.sub("", body).strip()
    if not answer:
        return _no_answer(
            query, REASON_NO_CITATION, candidates=candidates, rewritten_query=rewritten
        )

    by_id = {c["book_id"]: c for c in candidates}
    sources = [
        {
            "book_id": bid,
            "title": by_id[bid]["title"],
            "title_cn": by_id[bid]["title_cn"],
            "authors": by_id[bid]["authors"],
            "thumb_image": by_id[bid]["thumb_image"],
        }
        for bid in dict.fromkeys(cited)
    ]
    return AnswerResult(
        query=query,
        answer=answer,
        sources=sources,
        no_answer=False,
        rewritten_query=rewritten,
        candidates=candidates,
        has_more=len(pool) > len(sources),
        total_candidates=len(pool),
        total_titles=len(
            {_work_key(c["title_cn"] or c["title"]) for c in pool}
        ),
        intent=intent,
    )
