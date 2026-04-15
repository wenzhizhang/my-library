from typing import List, Optional, TYPE_CHECKING

from sqlalchemy import Column, Integer, String, ForeignKey
from sqlalchemy.orm import relationship, Mapped, mapped_column

from .base import Base


if TYPE_CHECKING:
    from .book import Book

class BookSeries(Base):
    __tablename__ = 'book_series'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    books: Mapped[List["Book"]] = relationship(back_populates="book_series")

    def __repr__(self):
        return f"<BookSeries(id={self.id}, name='{self.name}')>"
