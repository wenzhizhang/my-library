from typing import Optional, List, TYPE_CHECKING
from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .base import Base


if TYPE_CHECKING:
    from .book import Book

class Author(Base):
    __tablename__ = 'authors'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    name_cn: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    nation: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    dynasty: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    photo: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    books: Mapped[List["Book"]] = relationship("Book", secondary="book_authors", back_populates="authors")

    def __repr__(self):
        return f"<Author(id={self.id}, name='{self.name}')>"

    def __str__(self):
        author_name = self.name_cn if self.name_cn else self.name
        if self.dynasty:
            return f"[{self.dynasty}] {author_name}"
        else:
            return f"[{self.nation}] {author_name}"

