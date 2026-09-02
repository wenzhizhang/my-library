from typing import Optional, List, TYPE_CHECKING
from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Table
from sqlalchemy.orm import relationship, Mapped, mapped_column
from .base import Base

if TYPE_CHECKING:
    from .book import Book

# Many-to-many association: books in a collection
book_collection_items = Table(
    'book_collection_items',
    Base.metadata,
    Column('collection_id', Integer, ForeignKey('book_collections.id'), primary_key=True),
    Column('book_id', Integer, ForeignKey('books.id'), primary_key=True)
)


class BookCollection(Base):
    __tablename__ = 'book_collections'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now)
    weight: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    books: Mapped[List["Book"]] = relationship(secondary=book_collection_items, back_populates="collections")

    def __repr__(self):
        return f"<BookCollection(id={self.id}, name='{self.name}')>"
