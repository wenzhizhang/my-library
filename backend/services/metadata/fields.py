"""Canonical book metadata fields, source authority and merging rules."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import date
from typing import Any

# Fields the draft card and the book form understand.  Keep them in sync with
# ``BookCreation`` / ``IsbnLookupResponse``.
CANONICAL_FIELDS = (
    "isbn",
    "title_cn",
    "title",
    "author_names",
    "publisher_name",
    "publish_date",
    "pages",
    "price",
    "binding_type",
    "translator",
    "language",
    "summary",
    "introduction",
    "catalog",
    "thumb_image",
    "douban_score",
    "tags",
)

CORE_FIELDS = (
    "isbn",
    "title_cn",
    "author_names",
    "publisher_name",
    "publish_date",
    "price",
    "thumb_image",
)

# Per-field authority.  Anything not listed prefers Douban, then Dangdang,
# then the foreign fallbacks.
AUTHORITY: dict[str, tuple[str, ...]] = {
    "price": ("dangdang", "douban", "foreign"),
    "binding_type": ("douban", "dangdang", "foreign"),
    "pages": ("douban", "dangdang", "foreign"),
    "isbn": ("douban", "dangdang", "foreign"),
}
DEFAULT_AUTHORITY = ("douban", "dangdang", "foreign")

_PRICE_RE = re.compile(r"\d+(?:\.\d+)?")
_INT_RE = re.compile(r"\d+")
_DATE_RE = re.compile(r"(\d{4})(?:[-/年.](\d{1,2}))?(?:[-/月.](\d{1,2}))?")


@dataclass
class SourceResult:
    source: str
    fields: dict[str, Any]
    link: str = ""


@dataclass
class MergedMetadata:
    fields: dict[str, Any] = field(default_factory=dict)
    sources: dict[str, str] = field(default_factory=dict)
    missing: list[str] = field(default_factory=list)
    conflicts: dict[str, dict[str, Any]] = field(default_factory=dict)
    source: str = ""
    link: str = ""
    extra: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "fields": self.fields,
            "sources": self.sources,
            "missing": self.missing,
            "conflicts": self.conflicts,
            "source": self.source,
            "link": self.link,
            "extra": self.extra,
        }


def is_empty(value: Any) -> bool:
    if value is None:
        return True
    if isinstance(value, str):
        return not value.strip()
    if isinstance(value, (list, tuple, dict, set)):
        return not value
    return False


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def parse_price(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value) if value else None
    match = _PRICE_RE.search(clean_text(value))
    return float(match.group()) if match else None


def parse_int(value: Any) -> int | None:
    if isinstance(value, int):
        return value or None
    match = _INT_RE.search(clean_text(value))
    return int(match.group()) if match else None


def parse_publish_date(value: Any) -> date | None:
    """Parse "1982", "1982-03", "1982年3月1日" into a date."""
    match = _DATE_RE.search(clean_text(value))
    if not match:
        return None
    year = int(match.group(1))
    month = int(match.group(2) or 1)
    day = int(match.group(3) or 1)
    try:
        return date(year, max(1, min(12, month)), max(1, min(28, day)))
    except ValueError:
        return None


def _normalized(value: Any) -> Any:
    if isinstance(value, (list, tuple)):
        return tuple(sorted(clean_text(item).casefold() for item in value if clean_text(item)))
    if isinstance(value, (int, float)):
        return round(float(value), 2)
    return clean_text(value).casefold()


def _candidate_value(value: Any) -> Any:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return value
    if isinstance(value, (list, tuple)):
        return [clean_text(item) for item in value if clean_text(item)]
    return clean_text(value)


def merge_results(results: list[SourceResult]) -> MergedMetadata:
    """Merge per-source field maps using the authority table.

    The first non-empty value in the authority order wins.  If another source
    disagrees, the field is recorded in ``conflicts`` but never overwritten.
    """
    merged = MergedMetadata()
    by_source = {result.source: result for result in results if result}

    for name in CANONICAL_FIELDS:
        order = AUTHORITY.get(name, DEFAULT_AUTHORITY)
        candidates: dict[str, Any] = {}
        for result in results:
            value = _candidate_value(result.fields.get(name))
            if not is_empty(value):
                candidates[result.source] = value

        chosen_source = ""
        chosen_value = None
        for source in order:
            if source in candidates:
                chosen_source = source
                chosen_value = candidates[source]
                break
        if chosen_value is None and candidates:
            chosen_source = next(iter(candidates))
            chosen_value = candidates[chosen_source]

        if is_empty(chosen_value):
            merged.missing.append(name)
            continue
        merged.fields[name] = chosen_value
        merged.sources[name] = chosen_source

        if name == "thumb_image":
            # Different sources return different cover files for the same
            # edition; that is not a metadata conflict worth flagging.
            distinct = set()
        elif name == "publish_date":
            # "1982" and "1982-03-01" agree on the year; treat as one value.
            distinct = {clean_text(value)[:4] for value in candidates.values()}
        else:
            distinct = {_normalized(value) for value in candidates.values()}
        if len(distinct) > 1:
            merged.conflicts[name] = {
                source: (value[:160] if isinstance(value, str) else value)
                for source, value in candidates.items()
            }

    for result in results:
        for name, value in (result.fields or {}).items():
            if name in CANONICAL_FIELDS or is_empty(value):
                continue
            merged.extra.setdefault(name, _candidate_value(value))

    if results:
        counts: dict[str, int] = {}
        for source in merged.sources.values():
            counts[source] = counts.get(source, 0) + 1
        merged.source = max(counts, key=counts.get) if counts else results[0].source
        for result in results:
            if result.link:
                merged.link = result.link
                break
    return merged
