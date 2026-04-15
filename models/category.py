from typing import Optional, List, TYPE_CHECKING
from sqlalchemy import Column, Integer, String, ForeignKey, event
from sqlalchemy.orm import relationship, Mapped, mapped_column
from .base import Base


if TYPE_CHECKING:
    from .book import Book


class Category(Base):
    __tablename__ = 'categories'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    parent_id: Mapped[Optional[int]] = mapped_column(Integer, ForeignKey('categories.id'), nullable=True)
    intro: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    depth: Mapped[int] = mapped_column(Integer, default=0)
    path: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)

    parent_category = relationship("Category", remote_side=[id], backref="children")

    books: Mapped[List["Book"]] = relationship(back_populates="category")

    @property
    def parent(self):
        return self.parent_id

    def __repr__(self):
        return f"<Category(id={self.id}, name='{self.name}')>"


@event.listens_for(Category, 'before_insert')
@event.listens_for(Category, 'before_update')
def receive_before_insert_update(mapper, connection, target):
    # 这里的逻辑和你 Django save() 方法里的一模一样
    if target.parent_category:
        target.depth = target.parent_category.depth + 1
        target.path = f"{target.parent_category.path or target.parent_category.name} > {target.name}"
    else:
        target.depth = 0
        target.path = target.name