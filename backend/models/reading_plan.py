from typing import Optional, List, TYPE_CHECKING
from datetime import datetime, date
from sqlalchemy import Column, Integer, String, DateTime, Date, ForeignKey, Table
from sqlalchemy.orm import relationship, Mapped, mapped_column
from .base import Base

if TYPE_CHECKING:
    from .book import Book

# Many-to-many association: books in a reading plan
reading_plan_items = Table(
    'reading_plan_items',
    Base.metadata,
    Column('plan_id', Integer, ForeignKey('reading_plans.id'), primary_key=True),
    Column('book_id', Integer, ForeignKey('books.id'), primary_key=True)
)


class ReadingPlan(Base):
    __tablename__ = 'reading_plans'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    start_date: Mapped[Optional[date]] = mapped_column(Date, nullable=True)
    end_date: Mapped[Optional[date]] = mapped_column(Date, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now)

    books: Mapped[List["Book"]] = relationship(secondary=reading_plan_items, back_populates="reading_plans")

    def __repr__(self):
        return f"<ReadingPlan(id={self.id}, name='{self.name}')>"
