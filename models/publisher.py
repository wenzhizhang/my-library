from typing import Optional, List, TYPE_CHECKING
from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .base import Base


if TYPE_CHECKING:
    from .book import Book


class Publisher(Base):
    __tablename__ = 'publishers'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    logo: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    books: Mapped[List["Book"]] = relationship(back_populates="publisher")

    def __repr__(self):
        return f"<Publisher(id={self.id}, name='{self.name}')>"


class Brand(Base):
    __tablename__ = 'brands'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    books: Mapped[List["Book"]] = relationship(back_populates="brand")

    def __repr__(self):
        return f"<Brand(id={self.id}, name='{self.name}')>"
