"""Sync entities to the shared root.db for cross-user reference data.

All sync functions are fire-and-forget — a failure here never breaks
the user's primary write.  Uses raw SQL so root.db column differences
don't conflict with SQLAlchemy model definitions.
"""

from datetime import datetime

from sqlalchemy.orm import Session, selectinload
from sqlalchemy import text

from database import root_engine


# Book fields stored in root.db (public / reference fields only).
# Excludes: purchase_price, purchase_date, purchase_store, tags, link, bookshelf_id
_PUBLIC_BOOK_COLS = [
    "id", "isbn", "title_cn", "title", "translator", "publisher_id",
    "publish_date", "brand_id", "book_series_id", "binding_type",
    "paper_type", "pages", "book_count", "language", "compose_type",
    "price", "thumb_image", "category_id", "catalog",
    "introduction", "summary", "edition", "printing_info",
    "printed_number", "douban_score", "created_at", "updated_at",
]


# ── Low-level helpers ─────────────────────────────────────────────────────────

def _upsert(table: str, cols: list[str], row: dict) -> str | None:
    """INSERT OR REPLACE a row into root.db. Returns None on success, error string on failure."""
    placeholders = ", ".join("?" for _ in cols)
    values = tuple(row.get(c) for c in cols)
    sql = f"INSERT OR REPLACE INTO {table} ({', '.join(cols)}) VALUES ({placeholders})"
    try:
        with root_engine.connect() as conn:
            conn.exec_driver_sql(sql, values)
            conn.commit()
        return None
    except Exception as exc:
        return f"{table} id={row.get('id')}: {exc}"


def _insert_ignore(table: str, cols: list[str], row: dict) -> str | None:
    """INSERT OR IGNORE a row into root.db. Returns None on success, error string on failure."""
    placeholders = ", ".join("?" for _ in cols)
    values = tuple(row.get(c) for c in cols)
    sql = f"INSERT OR IGNORE INTO {table} ({', '.join(cols)}) VALUES ({placeholders})"
    try:
        with root_engine.connect() as conn:
            conn.exec_driver_sql(sql, values)
            conn.commit()
        return None
    except Exception as exc:
        return f"{table} id={row.get('id')}: {exc}"


# ── Entity sync ───────────────────────────────────────────────────────────────

def sync_author(author) -> str | None:
    return _upsert("authors", ["id", "name", "name_cn", "nation", "dynasty", "intro", "photo"], {
        "id": author.id, "name": author.name,
        "name_cn": getattr(author, "name_cn", None),
        "nation": getattr(author, "nation", None),
        "dynasty": getattr(author, "dynasty", None),
        "intro": getattr(author, "intro", None),
        "photo": getattr(author, "photo", None),
    })


def sync_publisher(publisher) -> str | None:
    return _upsert("publishers", ["id", "name", "intro", "logo"], {
        "id": publisher.id, "name": publisher.name,
        "intro": getattr(publisher, "intro", None),
        "logo": getattr(publisher, "logo", None),
    })


def sync_brand(brand) -> str | None:
    return _upsert("brands", ["id", "name", "intro"], {
        "id": brand.id, "name": brand.name,
        "intro": getattr(brand, "intro", None),
    })


def sync_category(category) -> str | None:
    return _upsert("categories", ["id", "name", "parent_id", "intro", "depth", "path"], {
        "id": category.id, "name": category.name,
        "parent_id": getattr(category, "parent_id", None),
        "intro": getattr(category, "intro", None),
        "depth": getattr(category, "depth", None),
        "path": getattr(category, "path", None),
    })


def sync_series(series) -> str | None:
    return _upsert("book_series", ["id", "name", "intro"], {
        "id": series.id, "name": series.name,
        "intro": getattr(series, "intro", None),
    })


def sync_book(book) -> str | None:
    return _upsert("books", _PUBLIC_BOOK_COLS, {
        "id": book.id,
        "isbn": getattr(book, "isbn", None),
        "title_cn": getattr(book, "title_cn", None),
        "title": book.title,
        "translator": getattr(book, "translator", None),
        "publisher_id": getattr(book, "publisher_id", None),
        "publish_date": getattr(book, "publish_date", None),
        "brand_id": getattr(book, "brand_id", None),
        "book_series_id": getattr(book, "book_series_id", None),
        "binding_type": getattr(book, "binding_type", None),
        "paper_type": getattr(book, "paper_type", None),
        "pages": getattr(book, "pages", None),
        "book_count": getattr(book, "book_count", None),
        "language": getattr(book, "language", None),
        "compose_type": getattr(book, "compose_type", None),
        "price": getattr(book, "price", None),
        "thumb_image": getattr(book, "thumb_image", None),
        "category_id": getattr(book, "category_id", None),
        "catalog": getattr(book, "catalog", None),
        "introduction": getattr(book, "introduction", None),
        "summary": getattr(book, "summary", None),
        "edition": getattr(book, "edition", None),
        "printing_info": getattr(book, "printing_info", None),
        "printed_number": getattr(book, "printed_number", None),
        "douban_score": getattr(book, "douban_score", None),
        "created_at": getattr(book, "created_at", None) or datetime.now(),
        "updated_at": getattr(book, "updated_at", None) or datetime.now(),
    })


def sync_book_author(book_id: int, author_id: int) -> str | None:
    return _insert_ignore("book_authors", ["book_id", "author_id"], {
        "book_id": book_id, "author_id": author_id,
    })


# ── Bulk sync ────────────────────────────────────────────────────────────────

def _existing_ids(table: str) -> set[int]:
    """Return set of IDs present in root.db for a given table."""
    try:
        with root_engine.connect() as conn:
            rows = conn.execute(text(f"SELECT id FROM {table}")).fetchall()
            return {r[0] for r in rows}
    except Exception:
        return set()


def _root_book_timestamps() -> dict[int, str]:
    """Return {id: updated_at} for all books in root.db."""
    try:
        with root_engine.connect() as conn:
            rows = conn.execute(text("SELECT id, updated_at FROM books")).fetchall()
            return {r[0]: r[1] for r in rows if r[1] is not None}
    except Exception:
        return {}


def _ensure_tables() -> None:
    """Create all required reference tables in root.db if they don't exist."""
    from models import Author, Publisher, Brand, BookSeries, Category  # noqa: F401

    # Reference tables via SQLAlchemy
    from models.base import Base
    Base.metadata.create_all(bind=root_engine, tables=[
        Author.__table__, Publisher.__table__, Brand.__table__,
        BookSeries.__table__, Category.__table__,
    ])

    # Books + book_authors via raw DDL
    with root_engine.begin() as conn:
        conn.exec_driver_sql("""
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY, isbn VARCHAR(50), title_cn VARCHAR(255),
                title VARCHAR(255) NOT NULL, translator VARCHAR(255),
                publisher_id INTEGER REFERENCES publishers(id),
                publish_date DATETIME, brand_id INTEGER REFERENCES brands(id),
                book_series_id INTEGER REFERENCES book_series(id),
                binding_type VARCHAR(50), paper_type VARCHAR(50),
                pages INTEGER, book_count INTEGER, language VARCHAR(50),
                compose_type VARCHAR(50), price FLOAT, thumb_image VARCHAR(500),
                category_id INTEGER REFERENCES categories(id),
                catalog VARCHAR(2000), introduction VARCHAR(2000),
                summary VARCHAR(2000), edition VARCHAR(100),
                printing_info VARCHAR(100), printed_number INTEGER,
                douban_score FLOAT,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.exec_driver_sql("""
            CREATE TABLE IF NOT EXISTS book_authors (
                book_id INTEGER REFERENCES books(id),
                author_id INTEGER REFERENCES authors(id),
                PRIMARY KEY (book_id, author_id)
            )
        """)


def sync_all_to_root(db: Session, differential: bool = True) -> dict:
    """Sync entities from a user DB session to root.db.

    Sync order follows foreign-key dependencies:
      1. Reference tables: authors, publishers, brands, book_series, categories
      2. Books (references all of the above)
      3. Book-author mappings (INSERT OR IGNORE, per book)

    Tables are created automatically if they don't exist.

    Returns {table: {total, synced}, _diagnostics, _errors}.
    """
    from models import Author, Publisher, Brand, BookSeries, Category, Book

    # Ensure tables exist before syncing
    _ensure_tables()

    result = {}
    errors = []

    # Diagnostics: check root.db tables
    diag = {}
    try:
        with root_engine.connect() as conn:
            tables = conn.execute(text(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            )).fetchall()
            diag["root_db_tables"] = [r[0] for r in tables]
            diag["root_db_path"] = str(root_engine.url)
    except Exception as exc:
        diag["root_db_error"] = str(exc)
    # Reference tables
    for model, sync_fn, table_name in [
        (Author, sync_author, "authors"),
        (Publisher, sync_publisher, "publishers"),
        (Brand, sync_brand, "brands"),
        (BookSeries, sync_series, "book_series"),
        (Category, sync_category, "categories"),
    ]:
        rows = db.query(model).all()
        total = len(rows)
        failed = 0
        if differential:
            existing = _existing_ids(table_name)
            synced = 0
            for obj in rows:
                if obj.id not in existing:
                    err = sync_fn(obj)
                    if err is None:
                        synced += 1
                    else:
                        failed += 1
                        errors.append(err)
        else:
            synced = 0
            for obj in rows:
                err = sync_fn(obj)
                if err is None:
                    synced += 1
                else:
                    failed += 1
                    errors.append(err)
        result[table_name] = {"total": total, "synced": synced}
        if failed:
            result[table_name]["failed"] = failed

    # Books
    books = db.query(Book).options(selectinload(Book.authors)).all()
    total_books = len(books)
    synced_books = 0
    failed_books = 0
    if differential:
        root_ts = _root_book_timestamps()
        for book in books:
            ut = getattr(book, "updated_at", None)
            user_ts = ut.isoformat() if ut else None
            root_book_ts = root_ts.get(book.id)
            if book.id not in root_ts or (user_ts and root_book_ts and user_ts > root_book_ts):
                err = sync_book(book)
                if err is None:
                    synced_books += 1
                else:
                    failed_books += 1
                    errors.append(err)
            for a in book.authors:
                sync_book_author(book.id, a.id)
    else:
        for book in books:
            err = sync_book(book)
            if err is None:
                synced_books += 1
            else:
                failed_books += 1
                errors.append(err)
            for a in book.authors:
                sync_book_author(book.id, a.id)
    result["books"] = {"total": total_books, "synced": synced_books}
    if failed_books:
        result["books"]["failed"] = failed_books

    result["_diagnostics"] = diag
    if errors:
        result["_errors"] = errors[:20]

    return result
