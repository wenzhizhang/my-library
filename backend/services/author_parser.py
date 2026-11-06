"""Parse author strings from book metadata into structured author refs.

Metadata often returns one string such as
``（南朝宋）刘义庆 著；张撝之 译注``.  The leading bracket is a dynasty or
nationality, semicolons separate contributors, and a trailing role word
(著/译注/校注/...) describes their contribution rather than their name.

This module is pure: database lookup/creation lives in
``services.author_resolver``.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Iterable, Optional

# Roles from the supported metadata grammar.  Longest first so "译注" wins
# over "注".
ROLE_WORDS = (
    "译注",
    "点校",
    "校注",
    "整理",
    "著",
    "注",
    "译",
    "校",
    "编",
    "绘",
)
_MULTI_CHAR_ROLES = {"译注", "点校", "校注", "整理"}
_ROLE_RE = re.compile(r"\s*(?:" + "|".join(ROLE_WORDS) + r")\s*$")
_BRACKET_RE = re.compile(r"^\s*[（(\[【]([^）)\]】]+)[）)\]】]\s*")
# "贝拉・博洛巴斯（Béla Bollobás）": the trailing bracket is the original
# foreign-language name, not part of the Chinese display name.
_TRAILING_ORIGINAL_RE = re.compile(
    r"\s*[（(【]([^（）()【】]*[A-Za-zÀ-ÖØ-öø-ÿ\u0370-\u03FF\u0400-\u04FF][^（）()【】]*)[）)】]\s*$"
)

DYNASTY_ALIASES = {
    "上古": "上古",
    "先秦": "先秦",
    "西周": "西周",
    "东周": "东周",
    "春秋": "春秋",
    "战国": "战国",
    "夏": "夏",
    "商": "商",
    "秦": "秦",
    "汉": "汉",
    "西汉": "西汉",
    "东汉": "东汉",
    "三国": "三国",
    "魏": "魏",
    "蜀": "蜀",
    "吴": "吴",
    "晋": "晋",
    "西晋": "西晋",
    "东晋": "东晋",
    "魏晋": "魏晋",
    "魏晋南北朝": "魏晋南北朝",
    "南北朝": "南北朝",
    "南朝": "南北朝",
    "北朝": "南北朝",
    "南朝宋": "南朝宋",
    "刘宋": "南朝宋",
    "南朝齐": "南朝齐",
    "南朝梁": "南朝梁",
    "南朝陈": "南朝陈",
    "隋": "隋",
    "唐": "唐",
    "五代": "五代",
    "五代十国": "五代十国",
    "宋": "宋",
    "北宋": "北宋",
    "南宋": "南宋",
    "辽": "辽",
    "金": "金",
    "西夏": "西夏",
    "元": "元",
    "明": "明",
    "清": "清",
    "民国": "民国",
    "现代": "现代",
    "当代": "当代",
}

NATION_ALIASES = {
    "中国": "中国",
    "中": "中国",
    "日": "日本",
    "日本": "日本",
    "美": "美国",
    "美国": "美国",
    "英": "英国",
    "英国": "英国",
    "法": "法国",
    "法国": "法国",
    "德": "德国",
    "德国": "德国",
    "俄": "俄罗斯",
    "俄罗斯": "俄罗斯",
    "苏": "苏联",
    "苏联": "苏联",
    "韩": "韩国",
    "韩国": "韩国",
    "意": "意大利",
    "意大利": "意大利",
    "加": "加拿大",
    "加拿大": "加拿大",
    "澳": "澳大利亚",
    "澳大利亚": "澳大利亚",
    "古希腊": "古希腊",
    "希腊": "希腊",
    "古罗马": "古罗马",
    "印度": "印度",
    "埃及": "埃及",
    "西班牙": "西班牙",
    "葡萄牙": "葡萄牙",
    "荷兰": "荷兰",
    "瑞士": "瑞士",
    "瑞典": "瑞典",
    "挪威": "挪威",
    "丹麦": "丹麦",
    "波兰": "波兰",
    "土耳其": "土耳其",
    "以色列": "以色列",
    "巴西": "巴西",
    "墨西哥": "墨西哥",
    "新加坡": "新加坡",
    "马来西亚": "马来西亚",
    "泰国": "泰国",
    "越南": "越南",
    "新西兰": "新西兰",
    "南非": "南非",
}

UNKNOWN_NATIONS = {"", "无", "未知"}


@dataclass(frozen=True)
class AuthorRef:
    name: str
    nation: str = ""
    dynasty: str = ""
    original_name: str = ""

    def as_dict(self) -> dict:
        return {
            "name": self.name,
            "original_name": self.original_name,
            "nation": self.nation,
            "dynasty": self.dynasty,
        }


def is_unknown_nation(value: str) -> bool:
    return (value or "").strip() in UNKNOWN_NATIONS


def normalize_dynasty(value: str) -> str:
    text = re.sub(r"\s+", "", (value or "").strip())
    return DYNASTY_ALIASES.get(text, text)


def normalize_nation(value: str) -> str:
    text = re.sub(r"\s+", "", (value or "").strip())
    if text in UNKNOWN_NATIONS:
        return ""
    return NATION_ALIASES.get(text, text)


def resolved_author(ref: AuthorRef) -> AuthorRef:
    """Apply the metadata defaults.

    Unknown nation/dynasty defaults to 中国/当代.  Only Chinese authors keep a
    dynasty; every other nationality has an empty dynasty.
    """
    nation = normalize_nation(ref.nation) or "中国"
    if nation != "中国":
        return AuthorRef(
            name=ref.name,
            nation=nation,
            dynasty="",
            original_name=ref.original_name,
        )
    dynasty = normalize_dynasty(ref.dynasty) or "当代"
    return AuthorRef(
        name=ref.name,
        nation="中国",
        dynasty=dynasty,
        original_name=ref.original_name,
    )


def refs_to_names(refs: Iterable[AuthorRef]) -> list[str]:
    return [ref.name for ref in refs]


def _classify_bracket(value: str) -> tuple[str, str]:
    text = re.sub(r"\s+", "", value or "")
    if not text:
        return "", ""
    if text in DYNASTY_ALIASES:
        return "中国", normalize_dynasty(text)
    if text in NATION_ALIASES:
        return normalize_nation(text), ""
    # Unknown bracket: do not turn it into a nationality or an arbitrary
    # dynasty.  The author then falls back to the China/contemporary defaults.
    return "", ""


def _strip_role(text: str) -> tuple[str, str]:
    match = _ROLE_RE.search(text)
    if not match:
        return text.strip(), ""
    matched = match.group(0)
    role = matched.strip()
    before = text[: match.start()]
    separated = bool(matched) and matched[0].isspace()
    # A one-character role without a space is probably part of a short name
    # ("王著"); longer names with a clear role suffix are safe to strip.
    if role in _MULTI_CHAR_ROLES or separated or len(before) >= 2:
        return before.strip(), role
    return text.strip(), ""


def parse_author_string(raw: str) -> list[AuthorRef]:
    text = str(raw or "").strip()
    if not text:
        return []
    refs: list[AuthorRef] = []
    for part in re.split(r"[;；，]|,\s*(?=[\[（(【])", text):
        piece = part.strip()
        if not piece:
            continue
        nation = ""
        dynasty = ""
        while True:
            bracket = _BRACKET_RE.match(piece)
            if not bracket:
                break
            bracket_nation, bracket_dynasty = _classify_bracket(bracket.group(1))
            nation = bracket_nation or nation
            dynasty = bracket_dynasty or dynasty
            piece = piece[bracket.end() :].strip()
        name, role = _strip_role(piece)
        original_name = ""
        original_match = _TRAILING_ORIGINAL_RE.search(name)
        if original_match:
            original_name = original_match.group(1).strip()
            name = name[: original_match.start()].strip()
        name = name.strip(" \t，,。.;；:：")
        if not name:
            continue
        if dynasty:
            nation = nation or "中国"
        refs.append(
            AuthorRef(
                name=name,
                nation=nation,
                dynasty=dynasty,
                original_name=original_name,
            )
        )
    return refs


def parse_author_list(values: Iterable[Any]) -> list[AuthorRef]:
    refs: list[AuthorRef] = []
    for value in values or []:
        if isinstance(value, AuthorRef):
            refs.append(value)
        elif isinstance(value, dict):
            name = str(value.get("name") or value.get("name_cn") or "").strip()
            if name:
                refs.append(
                    AuthorRef(
                        name=name,
                        nation=normalize_nation(str(value.get("nation") or "")),
                        dynasty=normalize_dynasty(str(value.get("dynasty") or "")),
                        original_name=str(
                            value.get("original_name")
                            or value.get("name_original")
                            or ""
                        ).strip(),
                    )
                )
        else:
            refs.extend(parse_author_string(str(value)))
    seen = set()
    unique = []
    for ref in refs:
        key = (ref.name, ref.nation, ref.dynasty, ref.original_name)
        if key not in seen:
            seen.add(key)
            unique.append(ref)
    return unique
