"""Generate compact book summaries, with an offline fallback.

The primary path calls DeepSeek's OpenAI-compatible endpoint with
``deepseek-flash`` (DeepSeek V4.1 Flash).  When the key is missing or the
request fails, ``summarize_introduction`` extracts representative sentences
locally so reindexing never depends on the network.
"""

import logging
import os
import re
import time
from collections import Counter
from typing import Optional

import httpx

logger = logging.getLogger(__name__)

# Default upper bound.  The actual cap is per book: ``document.summary_char_budget``
# subtracts the title/author/tag header and the tokenizer margin from the 512
# token window, so the summary never has to be cut by the model.
_SUMMARY_MAX_CHARS = 500
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[。！？!?；;])|\n+|(?<=[.!?])\s+")
_TOKEN_RE = re.compile(r"[\u4e00-\u9fff]+|[A-Za-z0-9]+")
_STOPWORDS = {
    "的", "了", "是", "在", "和", "与", "也", "有", "这", "那", "他", "她",
    "它", "们", "一个", "一种", "以及", "但是", "因为", "所以", "the", "a",
    "an", "of", "to", "and", "in", "is", "are", "was", "were", "for", "on",
    "with", "as", "by", "that", "this", "it",
}

_DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
_DEEPSEEK_MODEL = "deepseek-flash"
_DEEPSEEK_TIMEOUT = 60.0


def _summary_tokens(text: str) -> list[str]:
    """CJK bigrams + ASCII words, enough to rank sentences without jieba."""
    tokens = []
    for run in _TOKEN_RE.findall(text.lower()):
        if run.isascii():
            if run not in _STOPWORDS:
                tokens.append(run)
        elif len(run) == 1:
            if run not in _STOPWORDS:
                tokens.append(run)
        else:
            for i in range(len(run) - 1):
                token = run[i : i + 2]
                if token not in _STOPWORDS:
                    tokens.append(token)
    return tokens


def truncate_text(text: str, max_chars: int) -> str:
    """Cut at a sentence boundary when possible, never mid-character."""
    if len(text) <= max_chars:
        return text
    cut = text[:max_chars]
    for mark in "。！？!?；;.":
        idx = cut.rfind(mark)
        if idx >= max_chars * 0.6:
            return cut[: idx + 1].rstrip()
    return cut.rstrip()


def summarize_introduction(
    text: str, max_chars: int = _SUMMARY_MAX_CHARS
) -> str:
    """Extract the most representative sentences from an introduction.

    This is deliberately dependency-free (no LLM, no jieba): reindexing 2600
    books must stay fast and offline.  Sentences are ranked by term frequency,
    with a bonus for the opening sentence, then kept in their original order.
    """
    raw = (text or "").strip()
    normalized = re.sub(r"\s+", " ", raw).strip()
    if not normalized or len(normalized) <= max_chars:
        return normalized

    sentences = [
        re.sub(r"\s+", " ", s).strip()
        for s in _SENTENCE_SPLIT_RE.split(raw)
        if s.strip()
    ]
    if len(sentences) <= 1:
        return truncate_text(normalized, max_chars)

    frequencies = Counter(_summary_tokens(normalized))
    scores = []
    for i, sentence in enumerate(sentences):
        tokens = _summary_tokens(sentence)
        if not tokens:
            scores.append(0.0)
            continue
        score = sum(frequencies[t] for t in set(tokens)) / (len(tokens) ** 0.5)
        if i == 0:
            score *= 1.5
        scores.append(score)

    selected: list[tuple[int, str]] = []
    total = 0
    for i in sorted(range(len(sentences)), key=lambda idx: -scores[idx]):
        sentence = sentences[i]
        if total + len(sentence) > max_chars:
            continue
        selected.append((i, sentence))
        total += len(sentence)
        if total >= max_chars:
            break

    if not selected:
        return truncate_text(normalized, max_chars)
    selected.sort()
    separator = (
        " "
        if any(s.rstrip().endswith((".", "!", "?")) for _, s in selected)
        else ""
    )
    return truncate_text(separator.join(s for _, s in selected), max_chars)


def _clean_summary(content: str) -> str:
    text = (content or "").strip()
    text = re.sub(r"^```[a-zA-Z]*\s*|\s*```$", "", text).strip()
    text = re.sub(r"^(摘要|总结|内容摘要)\s*[:：]\s*", "", text).strip()
    return re.sub(r"\s+", " ", text).strip()


def _request_summary(
    title: str, authors: list[str], introduction: str
) -> tuple[Optional[str], dict]:
    """Call DeepSeek and return ``(summary, usage)``; usage is empty on failure."""
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    introduction = (introduction or "").strip()
    if not api_key or not introduction:
        return None, {}

    prompt = (
        f"书名：{title}\n"
        f"作者：{'; '.join(authors) if authors else '未知'}\n\n"
        "请把下面的图书简介压缩成 300-450 字的中文摘要。保留核心人物、主题、"
        "情节或内容要点；不要编造原文没有的信息；只输出摘要正文。\n\n"
        f"简介：\n{introduction}"
    )
    payload = {
        "model": _DEEPSEEK_MODEL,
        "messages": [
            {
                "role": "system",
                "content": "你是中文图书编辑。只输出摘要正文，不要标题、解释或 Markdown。",
            },
            {"role": "user", "content": prompt},
        ],
        # Summaries do not need chain-of-thought; non-thinking mode is faster
        # and cheaper.  Temperature is only effective in non-thinking mode.
        "thinking": {"type": "disabled"},
        "temperature": 0.3,
        "max_tokens": 1024,
        "stream": False,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    for attempt in range(2):
        try:
            response = httpx.post(
                _DEEPSEEK_URL, json=payload, headers=headers, timeout=_DEEPSEEK_TIMEOUT
            )
            if response.status_code == 429 or response.status_code >= 500:
                raise RuntimeError(f"HTTP {response.status_code}")
            response.raise_for_status()
            data = response.json()
            content = data["choices"][0]["message"]["content"]
            summary = _clean_summary(content)
            return summary, data.get("usage") or {}
        except Exception as exc:
            logger.warning(
                "DeepSeek summary failed (attempt %s/2): %s", attempt + 1, exc
            )
            if attempt == 0:
                time.sleep(2)

    return None, {}


def summarize_with_llm(
    title: str,
    authors: list[str],
    introduction: str,
    max_chars: int = _SUMMARY_MAX_CHARS,
) -> Optional[str]:
    """Summarize with DeepSeek V4.1 Flash; return None on any failure."""
    summary, _ = _request_summary(title, authors, introduction)
    return truncate_text(summary, max_chars) if summary else None
