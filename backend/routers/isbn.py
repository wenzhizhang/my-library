"""ISBN lookup router — queries external sources for book metadata.

Lookup chain: user's own DB → root.db → Douban → other APIs.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import List, Dict, Tuple

from database import get_db, get_root_db, root_engine
from models import Author, Publisher, Book, Category, Brand
from schemas.isbn import IsbnLookupResponse
from services.isbn_lookup import (
    IsbnNotFoundError,
    fetch_publisher_intro,
    lookup_isbn,
)
from services.sync_to_root import sync_author, sync_publisher, sync_brand


router = APIRouter(prefix="/api/isbn", tags=["isbn"])


def prefetch_authors(conn, book_id) -> List[Dict]:
    """
    Pre-fetch authors information from root database.
    :param conn: Connection to root database.
    :param book_id: The id of book.
    :return: List of author dict.
    """
    return [
            dict(r._mapping) for r in conn.execute(
                text(
                    "SELECT a.id, a.name, a.name_cn, a.nation, "
                    "a.dynasty, a.intro, a.photo "
                    "FROM authors a JOIN book_authors ba ON a.id = ba.author_id "
                    "WHERE ba.book_id = :bid"
                ), {"bid": book_id}
            ).fetchall()
        ]


def prefetch_publisher(conn, publisher_id) -> Dict | None:
    """
    Pre-fetch publisher information from root database.
    :param conn: Connection to root database.
    :param publisher_id: The id for publisher.
    :return: Publisher dict
    """
    publisher = conn.execute(
        text("SELECT id, name, intro, logo FROM publishers WHERE id = :pid"),
        {"pid": publisher_id}
    ).fetchone()

    if publisher:
        return dict(publisher._mapping)
    else:
        return None


def prefetch_brand(conn, brand_id) -> Dict | None:
    """
    Pre-fetch brand information from root database.
    :param conn: Connection to root database.
    :param brand_id: The id of brand.
    :return: Brand dict.
    """
    brand = conn.execute(
        text("SELECT id, name, intro FROM brands WHERE id = :brand_id"),
        {"brand_id": brand_id}
    ).fetchone()

    if brand:
        return dict(brand._mapping)
    else:
        return None


def prefetch_category(conn, category_id) -> Dict | None:
    """
    Pre-fetch category information from root database.
    :param conn: Connection to root database.
    :param category_id: The id of category.
    :return: Category dict.
    """
    category = conn.execute(
        text("SELECT name, intro, path FROM categories WHERE id = :category_id"),
        {"category_id": category_id}
    ).fetchone()

    if category:
        return dict(category._mapping)
    else:
        return None


def prefetch_book_series(conn, book_series_id) -> Dict | None:
    """
    Pre-fetch book series information from root database.
    :param conn: Connection to root database.
    :param book_series_id: The id of book series.
    :return: Book series dict.
    """
    book_series = conn.execute(
        text("SELECT name, intro FROM book_series WHERE id = :book_series_id"),
        {"book_series_id": book_series_id}
    ).fetchone()

    if book_series:
        return dict(book_series._mapping)
    else:
        return None


def retrieve_or_create_authors(db: Session, author_dict_list: List[Dict]):
    """
    Check if the author name exists in user database. If exists, retrieve it, else create it in user database.
    :param db: Database session to user database.
    :param author_dict_list: The list of authors information in dict format from root database.
    :return: Author ID list and author name list.
    """
    author_ids = []
    author_names = []
    for author in author_dict_list:
        name = author.get("name") or author.get("name_cn")
        if not name:
            continue
        existing = db.query(Author).filter(
            (Author.name == name) | (Author.name_cn == name)
        ).first()
        if existing:
            author_ids.append(existing.id)
            author_names.append(existing.name_cn or existing.name)
        else:
            new_author = Author(
                name=author["name"], name_cn=author.get("name_cn"),
                nation=author.get("nation") or "无", dynasty=author.get("dynasty"),
                intro=author.get("intro"), photo=author.get("photo"),
            )
            db.add(new_author)
            db.commit()
            db.refresh(new_author)
            author_ids.append(new_author.id)
            author_names.append(new_author.name_cn or new_author.name)
    return author_ids, author_names


def retrieve_or_create_publisher(db: Session, publisher_info: Dict):
    """
    Check if the publisher exists in user database. If exists, retrieve it else create it in user database.
    :param db: Database session to uer database.
    :param publisher_info: The publisher information in dict format from root database.
    :return: Publisher ID and publisher name.
    """
    publisher = db.query(Publisher).filter(Publisher.name == publisher_info["name"]).first()

    if publisher:
        publisher_id = publisher.id
        publisher_name = publisher.name
    else:
        new_publisher = Publisher(
            name=publisher_info["name"], intro=publisher_info.get("intro"), logo=publisher_info.get("logo"),
        )
        db.add(new_publisher)
        db.commit()
        db.refresh(new_publisher)
        publisher_id = new_publisher.id
        publisher_name = new_publisher.name
    return publisher_id, publisher_name


@router.get("/{isbn}", response_model=IsbnLookupResponse)
async def get_isbn_info(
    isbn: str,
    db: Session = Depends(get_db),
    root_db: Session = Depends(get_root_db),
):
    """Look up book metadata by ISBN.

    Chain: user DB → root.db → Douban → other APIs.
    Authors and publishers are resolved against user DB first, then root.db,
    then created and synced to root.db.
    """
    # ── Book already exists? user DB → root.db ──
    book = db.query(Book).filter(Book.isbn == isbn).first()
    from_root = False
    if not book:
        with root_engine.connect() as rconn:
            row = rconn.execute(
                text("SELECT * FROM books WHERE isbn = :isbn"), {"isbn": isbn}
            ).fetchone()

            if row:
                from_root = True
                book = dict(row._mapping)

                # Pre-fetch authors
                authors = prefetch_authors(rconn, book["id"])
                book["_authors"] = authors

                # Pre-fetch publisher
                if book.get("publisher_id"):
                    publisher = prefetch_publisher(rconn, book.get("publisher_id"))
                    if publisher:
                        book["_publisher"] = publisher

                # Pre-fetch brand
                if book.get("brand_id"):
                    brand = prefetch_brand(rconn, book.get("brand_id"))
                    if brand:
                        book["_brand"] = brand

                # Pre-fetch category
                if book.get("category_id"):
                    category = prefetch_category(rconn, book.get("category_id"))
                    if category:
                        book["_category"] = category

                # Pre-fetch book series
                if book.get("book_series_id"):
                    book_series = prefetch_book_series(rconn, book.get("book_series_id"))

                    if book_series:
                        book["_book_series"] = book_series
    if book:
        author_ids = []
        author_names = []
        if from_root:
            author_ids, author_names = retrieve_or_create_authors(db, book.get("_authors", []))

            publisher_id = None
            publisher_name = None
            pub = book.get("_publisher")
            if pub:
                publisher_id, publisher_name = retrieve_or_create_publisher(db, pub)

            # Only return if root.db had authors; otherwise fall through to
            # external APIs to resolve them (same as user-DB path below).
            if author_ids:
                # Resolve IDs from pre-fetched root.db data for brand/category/series
                _brand = book.get("_brand")
                _category = book.get("_category")
                _series = book.get("_book_series")
                return IsbnLookupResponse(
                    isbn=book.get("isbn") or "",
                    title=book.get("title") or "",
                    title_cn=book.get("title_cn") or "",
                    publisher_name=publisher_name or "",
                    publisher_id=publisher_id,
                    publish_date=book.get("publish_date") or "",
                    pages=book.get("pages"),
                    price=book.get("price"),
                    summary=book.get("summary") or "",
                    introduction=book.get("introduction") or "",
                    translator=book.get("translator") or "",
                    language=book.get("language") or "",
                    binding_type=book.get("binding_type") or "",
                    paper_type=book.get("paper_type") or "",
                    compose_type=book.get("compose_type") or "",
                    catalog=book.get("catalog") or "",
                    edition=book.get("edition") or "",
                    printing_info=book.get("printing_info") or "",
                    douban_score=book.get("douban_score"),
                    thumb_image=book.get("thumb_image") or "",
                    book_count=book.get("book_count"),
                    brand_id=_brand.get("id") if _brand else None,
                    brand_name=_brand.get("name") if _brand else None,
                    category_id=_category.get("id") if _category else None,
                    category_path=_category.get("path") if _category else None,
                    book_series_id=_series.get("id") if _series else None,
                    book_series_name=_series.get("name") if _series else "",
                    author_names=author_names,
                    author_ids=author_ids,
                )
        else:
            author_ids = [author.id for author in book.authors]
            author_names = [author.name_cn or author.name for author in book.authors]

            publisher_name = book.publisher.name if book.publisher else None

            # If the book has no authors linked (e.g. created from a buggy
            # frontend), fall through to external APIs to resolve them instead
            # of returning empty author data.
            if author_ids:
                return IsbnLookupResponse(
                    isbn=book.isbn or "",
                    title=book.title or "",
                    title_cn=getattr(book, "title_cn", None) or "",
                    publisher_name=publisher_name or "",
                    publisher_id=book.publisher_id,
                    publish_date=book.publish_date.isoformat() if book.publish_date else "",
                    pages=book.pages,
                    price=book.price,
                    summary=getattr(book, "summary", None) or "",
                    introduction=getattr(book, "introduction", None) or "",
                    translator=getattr(book, "translator", None) or "",
                    language=getattr(book, "language", None) or "",
                    binding_type=getattr(book, "binding_type", None) or "",
                    paper_type=getattr(book, "paper_type", None) or "",
                    compose_type=getattr(book, "compose_type", None) or "",
                    catalog=getattr(book, "catalog", None) or "",
                    edition=getattr(book, "edition", None) or "",
                    printing_info=getattr(book, "printing_info", None) or "",
                    douban_score=getattr(book, "douban_score", None),
                    thumb_image=getattr(book, "thumb_image", None) or "",
                    book_count=getattr(book, "book_count", None),
                    printed_number=getattr(book, "printed_number", None),
                    brand_id=getattr(book, "brand_id", None),
                    book_series_id=getattr(book, "book_series_id", None),
                    category_id=getattr(book, "category_id", None),
                    author_ids=author_ids,
                    author_names=author_names,
                )

    # ── Not found locally, hit external APIs ──
    try:
        info = await lookup_isbn(isbn)
    except IsbnNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc))

    # Resolve / create authors   (user DB → root.db → create)
    author_ids = []
    for name in info.author_names:
        if not name:
            continue
        # 1. user's own DB
        existing = (
            db.query(Author)
            .filter((Author.name == name) | (Author.name_cn == name))
            .first()
        )
        # 2. root.db — copy to user DB if found here
        if not existing:
            root_author = (
                root_db.query(Author)
                .filter((Author.name == name) | (Author.name_cn == name))
                .first()
            )
            if root_author:
                existing = Author(
                    name=root_author.name,
                    name_cn=getattr(root_author, "name_cn", None),
                    nation=getattr(root_author, "nation", "无"),
                    dynasty=getattr(root_author, "dynasty", None),
                    intro=getattr(root_author, "intro", None),
                    photo=getattr(root_author, "photo", None),
                )
                db.add(existing)
                db.commit()
                db.refresh(existing)
        if existing:
            author_ids.append(existing.id)
        else:
            # 3. create new
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
            sync_author(new_author)

    # Resolve / create publisher  (user DB → root.db → create)
    publisher_id = None
    if info.publisher_name:
        # 1. user's own DB
        existing = (
            db.query(Publisher)
            .filter(Publisher.name == info.publisher_name)
            .first()
        )
        # 2. root.db — copy to user DB if found here
        if not existing:
            root_pub = (
                root_db.query(Publisher)
                .filter(Publisher.name == info.publisher_name)
                .first()
            )
            if root_pub:
                existing = Publisher(
                    name=root_pub.name,
                    intro=getattr(root_pub, "intro", None),
                    logo=getattr(root_pub, "logo", None),
                )
                db.add(existing)
                db.commit()
                db.refresh(existing)
        if existing:
            publisher_id = existing.id
        else:
            # 3. create new
            publisher_intro = await fetch_publisher_intro(info.publisher_name)
            new_pub = Publisher(
                name=info.publisher_name,
                intro=publisher_intro or None,
            )
            db.add(new_pub)
            db.commit()
            db.refresh(new_pub)
            publisher_id = new_pub.id
            sync_publisher(new_pub)

    # Resolve / create brand  (user DB → root.db → create)
    brand_id = None
    if info.brand_id:
        # Get brand name from root.db via the ID
        root_brand = root_db.query(Brand).filter(Brand.id == info.brand_id).first()
        brand_name = root_brand.name if root_brand else None
        if brand_name:
            # 1. user's own DB
            existing = db.query(Brand).filter(Brand.name == brand_name).first()
            # 2. root.db — copy to user DB if found here (may differ from above if brand_id was
            #    from a different source, but root.db still has the authoritative name)
            if not existing and root_brand:
                existing = Brand(
                    name=root_brand.name,
                    intro=getattr(root_brand, "intro", None),
                )
                db.add(existing)
                db.commit()
                db.refresh(existing)
            if existing:
                brand_id = existing.id
            else:
                # 3. create new
                new_brand = Brand(name=brand_name)
                db.add(new_brand)
                db.commit()
                db.refresh(new_brand)
                brand_id = new_brand.id
                sync_brand(new_brand)

    # Resolve / create category  (user DB → root.db → create)
    category_id = None
    if info.category_id:
        root_cat = root_db.query(Category).filter(Category.id == info.category_id).first()
        cat_name = root_cat.name if root_cat else None
        if cat_name:
            # 1. user's own DB
            existing = db.query(Category).filter(Category.name == cat_name).first()
            # 2. root.db — copy to user DB
            if not existing and root_cat:
                existing = Category(
                    name=root_cat.name,
                    parent_id=getattr(root_cat, "parent_id", None),
                    intro=getattr(root_cat, "intro", None),
                    depth=getattr(root_cat, "depth", 0),
                    path=getattr(root_cat, "path", None),
                )
                db.add(existing)
                db.commit()
                db.refresh(existing)
            if existing:
                category_id = existing.id
            else:
                new_cat = Category(name=cat_name)
                db.add(new_cat)
                db.commit()
                db.refresh(new_cat)
                category_id = new_cat.id

    # Derive brand/category names from the resolved IDs
    brand_name = ""
    if brand_id:
        b = db.query(Brand).filter(Brand.id == brand_id).first()
        if b:
            brand_name = b.name
    category_path = ""
    if category_id:
        c = db.query(Category).filter(Category.id == category_id).first()
        if c:
            category_path = c.path or c.name

    return IsbnLookupResponse(
        isbn=info.isbn or "",
        title=info.title or "",
        title_cn=info.title_cn or "",
        publisher_name=info.publisher_name or "",
        publisher_id=publisher_id,
        publish_date=info.publish_date or "",
        pages=info.pages,
        price=info.price,
        summary=info.summary or "",
        introduction=info.introduction or "",
        translator=info.translator or "",
        language=info.language or "",
        binding_type=info.binding_type or "",
        paper_type=getattr(info, "paper_type", None) or "",
        compose_type=getattr(info, "compose_type", None) or "",
        catalog=info.catalog or "",
        edition=getattr(info, "edition", None) or "",
        printing_info=getattr(info, "printing_info", None) or "",
        douban_score=info.douban_score,
        thumb_image=info.thumb_image or "",
        book_count=getattr(info, "book_count", None),
        printed_number=getattr(info, "printed_number", None),
        source=info.source or "",
        link=info.link or "",
        tag_names=info.tag_names or [],
        author_intro=info.author_intro or "",
        author_names=info.author_names or [],
        author_ids=author_ids or [],
        brand_id=brand_id,
        brand_name=brand_name,
        category_id=category_id,
        category_path=category_path,
        book_series_id=getattr(info, "book_series_id", None),
        book_series_name=getattr(info, "book_series_name", None) or "",
    )
