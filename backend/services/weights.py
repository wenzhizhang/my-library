"""Recompute stored weight columns (book counts) for the six entity tables.

weight = number of non-wishlist, non-archived books linked to each object.
Full-table recompute after every book/collection mutation — no incremental
deltas, so the stored columns can never drift from the books table.
"""

from sqlalchemy import select, func, update
from sqlalchemy.orm import Session

from models import (
    Author,
    Publisher,
    Brand,
    BookSeries,
    BookCollection,
    Category,
    Book,
    book_authors,
    book_collection_items,
)


def recompute_weights(db: Session) -> None:
    """Recompute weight for all six entity tables from the books table."""
    # Authors — many-to-many via book_authors
    db.execute(
        update(Author).values(
            weight=select(func.count(Book.id))
            .select_from(book_authors)
            .join(Book, Book.id == book_authors.c.book_id)
            .where(
                book_authors.c.author_id == Author.id,
                Book.in_wish == False,
                Book.archived == False,
            )
            .scalar_subquery()
        )
    )

    # Publishers, brands, series, categories — one-to-many via FK
    db.execute(
        update(Publisher).values(
            weight=select(func.count(Book.id))
            .where(
                Book.publisher_id == Publisher.id,
                Book.in_wish == False,
                Book.archived == False,
            )
            .scalar_subquery()
        )
    )
    db.execute(
        update(Brand).values(
            weight=select(func.count(Book.id))
            .where(
                Book.brand_id == Brand.id,
                Book.in_wish == False,
                Book.archived == False,
            )
            .scalar_subquery()
        )
    )
    db.execute(
        update(BookSeries).values(
            weight=select(func.count(Book.id))
            .where(
                Book.book_series_id == BookSeries.id,
                Book.in_wish == False,
                Book.archived == False,
            )
            .scalar_subquery()
        )
    )
    db.execute(
        update(Category).values(
            weight=select(func.count(Book.id))
            .where(
                Book.category_id == Category.id,
                Book.in_wish == False,
                Book.archived == False,
            )
            .scalar_subquery()
        )
    )

    # Book collections — many-to-many via book_collection_items
    db.execute(
        update(BookCollection).values(
            weight=select(func.count(Book.id))
            .select_from(book_collection_items)
            .join(Book, Book.id == book_collection_items.c.book_id)
            .where(
                book_collection_items.c.collection_id == BookCollection.id,
                Book.in_wish == False,
                Book.archived == False,
            )
            .scalar_subquery()
        )
    )

    db.commit()
