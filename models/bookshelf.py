from typing import Optional, List, TYPE_CHECKING
from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import relationship, Mapped, mapped_column
from .base import Base


if TYPE_CHECKING:
    from .book import Book


class Bookshelf(Base):
    __tablename__ = 'bookshelves'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    books: Mapped[List["Book"]] = relationship(back_populates="bookshelf")

    def __repr__(self):
        return f"<Bookshelf(id={self.id}, name='{self.name}')>"
