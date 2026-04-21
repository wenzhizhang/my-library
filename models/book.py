from typing import List, Optional, TYPE_CHECKING, Dict, Any

from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, ForeignKey, Table, JSON, or_
from sqlalchemy.orm import relationship, Mapped, mapped_column, Query
from datetime import datetime

from .publisher import Publisher, Brand
from .category import Category
from .bookshelf import Bookshelf
from .base import Base

# Association tables
book_authors = Table(
    'book_authors',
    Base.metadata,
    Column('book_id', Integer, ForeignKey('books.id'), primary_key=True),
    Column('author_id', Integer, ForeignKey('authors.id'), primary_key=True)
)


if TYPE_CHECKING:
    from .author import Author
    from .series import BookSeries


class Book(Base):
    __tablename__ = 'books'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    isbn: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    title_cn: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    translator: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    publisher_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey('publishers.id'), nullable=True)
    publish_date: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    brand_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey('brands.id'), nullable=True)
    book_series_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey('book_series.id'), nullable=True)
    binding_type: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    paper_type: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    pages: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    book_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    language: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    compose_type: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    price: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    purchase_price: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    purchase_date: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    thumb_image: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    link: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    category_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey('categories.id'), nullable=True)
    bookshelf_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey('bookshelves.id'), nullable=True)
    read_state: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    catalog: Mapped[Optional[str]] = mapped_column(String(2000), nullable=True)
    introduction: Mapped[Optional[str]] = mapped_column(String(2000), nullable=True)
    summary: Mapped[Optional[str]] = mapped_column(String(2000), nullable=True)
    registered: Mapped[bool] = mapped_column(Boolean, default=False)
    edition: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    printing_info: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    printed_number: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    douban_score: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    purchase_store: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    tags: Mapped[Optional[List[str]]] = mapped_column(JSON, nullable=True)
    in_wish: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, onupdate=datetime.now)

    # Relationships
    publisher: Mapped[Optional["Publisher"]] = relationship(back_populates="books")
    brand: Mapped[Optional["Brand"]] = relationship(back_populates="books")
    book_series: Mapped[Optional["BookSeries"]] = relationship(back_populates="books")
    category: Mapped[Optional["Category"]] = relationship(back_populates="books")
    bookshelf: Mapped[Optional["Bookshelf"]] = relationship(back_populates="books")

    authors: Mapped[list["Author"]] = relationship(secondary=book_authors, back_populates="books")

    def __repr__(self):
        return f"<Book(id={self.id}, title='{self.title}')>"


class BookSearchStrategy:

    @staticmethod
    def apply_filters(query, filters):
        joined = set()
        conditions = []

        if filters.get("isbn"):
            conditions.append(Book.isbn.ilike(f"%{filters['isbn']}%"))

        if filters.get("title"):
            conditions.append(
                or_(Book.title.ilike(f"%{filters['title']}%"), Book.title_cn.ilike(f"%{filters['title']}%"))
            )

        if filters.get("author"):
            author_cls = Book.authors.property.mapper.class_

            if "authors" not in joined:
                query = query.join(Book.authors)
                joined.add("authors")
            conditions.append(
                or_(
                    author_cls.name.ilike(f"%{filters['author']}%"),
                    author_cls.name_cn.ilike(f"%{filters['author']}%")
                )
            )

        if filters.get("publisher"):
            if "publisher" not in joined:
                query = query.join(Book.publisher)
                joined.add("publisher")
            conditions.append(Publisher.name.ilike(f"%{filters['publisher']}%"))

        if filters.get("min_price"):
            conditions.append(Book.price >= filters["min_price"])

        if filters.get("max_price"):
            conditions.append(Book.price <= filters["max_price"])

        if conditions:
            query = query.filter(*conditions)

        return query.distinct()


