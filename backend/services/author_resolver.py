"""Persist parsed author refs: match by name + nation/dynasty, then create."""

from __future__ import annotations

from typing import Any, Iterable, Optional

from sqlalchemy import or_
from sqlalchemy.orm import Session

from models import Author

from services.author_parser import (
    AuthorRef,
    is_unknown_nation,
    normalize_dynasty,
    normalize_nation,
    parse_author_list,
    resolved_author,
)


def compatible_author(author: Author, ref: AuthorRef) -> bool:
    existing_nation = normalize_nation(author.nation or "")
    if ref.nation and ref.nation != "中国":
        return (
            not existing_nation
            or is_unknown_nation(existing_nation)
            or existing_nation == ref.nation
        )
    if existing_nation and existing_nation not in ("中国",):
        # Unknown nation is allowed to be filled; a different known nation is
        # not.
        return is_unknown_nation(existing_nation)
    if ref.dynasty and author.dynasty:
        if normalize_dynasty(author.dynasty) != normalize_dynasty(ref.dynasty):
            return False
    return True


def fill_author_metadata(author: Author, ref: AuthorRef) -> None:
    """Fill a matched author's metadata using the resolved ref."""
    resolved = resolved_author(ref)
    if resolved.original_name and author.name == author.name_cn:
        author.name = resolved.original_name
    if is_unknown_nation(author.nation or ""):
        author.nation = resolved.nation
    if author.nation != "中国":
        author.dynasty = None
        return
    if not author.dynasty:
        author.dynasty = resolved.dynasty


def find_existing_author(db: Session, ref: AuthorRef) -> Optional[Author]:
    resolved = resolved_author(ref)
    names = {resolved.name}
    if resolved.original_name:
        names.add(resolved.original_name)
    candidates = (
        db.query(Author)
        .filter(or_(Author.name.in_(names), Author.name_cn.in_(names)))
        .all()
    )
    for author in candidates:
        if compatible_author(author, resolved):
            return author
    return None


def find_or_create_author(
    db: Session,
    ref: AuthorRef,
    *,
    sync: bool = False,
) -> Author:
    resolved = resolved_author(ref)
    existing = find_existing_author(db, resolved)
    if existing is not None:
        fill_author_metadata(existing, resolved)
        return existing
    author = Author(
        name=resolved.original_name or resolved.name,
        name_cn=resolved.name,
        nation=resolved.nation,
        dynasty=resolved.dynasty or None,
    )
    db.add(author)
    db.flush()
    db.refresh(author)
    if sync:
        try:
            from services.sync_to_root import sync_author

            sync_author(author)
        except Exception:
            pass
    return author


def resolve_author_ids(db: Session, values: Iterable[Any], *, sync: bool = False) -> list[int]:
    ids: list[int] = []
    for ref in parse_author_list(values):
        author = find_or_create_author(db, ref, sync=sync)
        if author.id not in ids:
            ids.append(author.id)
    return ids


def copy_author_from_candidate(
    db: Session, candidate: Author, ref: AuthorRef
) -> Author:
    """Clone a root-DB author, applying the parsed nation/dynasty rules."""
    resolved = resolved_author(ref)
    nation = resolved.nation if is_unknown_nation(candidate.nation or "") else candidate.nation
    dynasty = None if nation != "中国" else (candidate.dynasty or resolved.dynasty)
    author = Author(
        name=resolved.original_name or candidate.name,
        name_cn=getattr(candidate, "name_cn", None) or resolved.name,
        nation=nation,
        dynasty=dynasty,
        intro=getattr(candidate, "intro", None),
        photo=getattr(candidate, "photo", None),
    )
    db.add(author)
    db.flush()
    return author


def backfill_intro(author: Author, intro: str) -> None:
    if intro and not author.intro:
        author.intro = intro
