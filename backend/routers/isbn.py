"""ISBN lookup router — queries external sources for book metadata."""

import logging

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from database import get_db
from models import Author, Publisher
from schemas.isbn import IsbnLookupResponse
from services.isbn_lookup import (
    IsbnNotFoundError,
    fetch_publisher_intro,
    lookup_isbn,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/isbn", tags=["isbn"])


@router.get("/{isbn}", response_model=IsbnLookupResponse)
async def get_isbn_info(isbn: str, db: Session = Depends(get_db)):
    """Look up book metadata by ISBN.

    Primary source: Douban (requires DOUBAN_KEY env var).
    Fallbacks: Open Library, Google Books.

    Authors and publishers are checked against the database — existing
    records are linked by ID, missing ones are created.  Publisher
    introductions are fetched from Baidu Baike when the publisher
    doesn't exist yet.
    """
    try:
        info = await lookup_isbn(isbn)
    except IsbnNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc))

    # Resolve / create authors
    author_ids = []
    for name in info.author_names:
        if not name:
            continue
        existing = (
            db.query(Author)
            .filter((Author.name == name) | (Author.name_cn == name))
            .first()
        )
        if existing:
            author_ids.append(existing.id)
        else:
            new_author = Author(
                name=name,
                name_cn=name,
                nation="无",
                dynasty=None,
                intro=info.author_intro or None,
            )
            db.add(new_author)
            db.commit()
            db.refresh(new_author)
            author_ids.append(new_author.id)

    # Resolve / create publisher
    publisher_id = None
    if info.publisher_name:
        existing = (
            db.query(Publisher)
            .filter(Publisher.name == info.publisher_name)
            .first()
        )
        if existing:
            publisher_id = existing.id
        else:
            # Try Baidu Baike for introduction
            publisher_intro = await fetch_publisher_intro(info.publisher_name)
            new_pub = Publisher(
                name=info.publisher_name,
                intro=publisher_intro or None,
            )
            db.add(new_pub)
            db.commit()
            db.refresh(new_pub)
            publisher_id = new_pub.id

    return IsbnLookupResponse(
        isbn=info.isbn,
        title=info.title,
        title_cn=info.title_cn,
        publisher_name=info.publisher_name,
        publisher_id=publisher_id,
        publish_date=info.publish_date,
        pages=info.pages,
        price=info.price,
        summary=info.summary,
        introduction=info.introduction,
        thumb_image=info.thumb_image,
        binding_type=info.binding_type,
        douban_score=info.douban_score,
        author_names=info.author_names,
        author_ids=author_ids,
        translator=info.translator,
        language=info.language,
        source=info.source,
        link=info.link,
        catalog=info.catalog,
        tag_names=info.tag_names,
        author_intro=info.author_intro,
    )
