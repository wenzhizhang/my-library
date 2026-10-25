"""Loose text keys used for titles, queries and author names."""

import re

_NON_WORD_RE = re.compile(r"[\W_]+")


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


