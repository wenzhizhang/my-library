from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from typing import List, Optional

from models import Category, Book
from models.book import apply_book_sort, apply_book_q
from schemas.category import CategoryCreation, CategoryUpdate, CategoryResponse
from database import get_db
from serializers import serialize_book
from auth import get_current_user_id
from services.sync_to_root import sync_category

router = APIRouter(prefix="/api/categories", tags=["categories"])

@router.post("/", response_model=CategoryResponse)
def create_category(category: CategoryCreation, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    db_category = Category(
        name=category.name,
        parent_id=category.parent,
        intro=category.intro
    )
    db.add(db_category)
    db.commit()
    db.refresh(db_category)
    sync_category(db_category)
    return db_category

@router.get("/")
def read_categories(page: int = 1, limit: int = 10, sort_by: str = 'name', q: Optional[str] = None, db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Category)
    if sort_by == 'name':
        query = query.order_by(Category.name)
    elif sort_by == 'created_at':
        query = query.order_by(Category.created_at)
    if q:
        query = query.filter(Category.name.ilike(f'%{q}%'))
    categories = query.offset(offset).limit(limit).all()
    total_categories = query.count()
    total_pages = (total_categories + limit - 1) // limit

    categories_data = []
    for c in categories:
        categories_data.append({
            "id": c.id,
            "name": c.name,
            "parent_id": c.parent_id,
            "intro": c.intro,
            "depth": c.depth,
            "path": c.path,
        })

    return {
        "categories": categories_data,
        "total_pages": total_pages,
        "total_categories": total_categories
    }

@router.get("/{category_id}", response_model=CategoryResponse)
def read_category(category_id: int, db: Session = Depends(get_db)):
    category = db.query(Category).options(joinedload(Category.books).joinedload(Book.authors)).filter(Category.id == category_id).first()
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    category.books = [b for b in category.books if not b.in_wish]
    return category


@router.get("/{category_id}/books")
def read_category_books(category_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    category = db.query(Category).filter(Category.id == category_id).first()
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.category_id == category_id,
        Book.in_wish == False
    )
    query = apply_book_q(query, q)
    total_books = query.count()
    total_pages = (total_books + limit - 1) // limit
    offset = (page - 1) * limit
    query = apply_book_sort(query, sort_by)
    books = query.offset(offset).limit(limit).all()
    return {
        "books": [serialize_book(b) for b in books],
        "total_pages": total_pages,
        "total_books": total_books
    }
@router.put("/{category_id}", response_model=CategoryResponse)
def update_category(category_id: int, category_update: CategoryUpdate, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    category = db.query(Category).filter(Category.id == category_id).first()
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    for key, value in category_update.dict(exclude_unset=True).items():
        if key == 'parent':
            category.parent_id = value
        else:
            setattr(category, key, value)
    db.commit()
    db.refresh(category)
    sync_category(category)
    return category

@router.delete("/{category_id}")
def delete_category(category_id: int, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    category = db.query(Category).filter(Category.id == category_id).first()
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    db.delete(category)
    db.commit()
    return {"message": "Category deleted"}
