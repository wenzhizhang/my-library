from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import List

from models import Category
from schemas.category import CategoryCreation, CategoryUpdate, CategoryResponse
from database import get_db

router = APIRouter(prefix="/api/categories", tags=["categories"])

@router.post("/", response_model=CategoryResponse)
def create_category(category: CategoryCreation, db: Session = Depends(get_db)):
    db_category = Category(
        name=category.name,
        parent_id=category.parent,
        intro=category.intro
    )
    db.add(db_category)
    db.commit()
    db.refresh(db_category)
    return db_category

@router.get("/")
def read_categories(page: int = 1, limit: int = 10, sort_by: str = 'name', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Category)
    if sort_by == 'name':
        query = query.order_by(Category.name)
    elif sort_by == 'created_at':
        query = query.order_by(Category.created_at)
    categories = query.offset(offset).limit(limit).all()
    total_categories = db.query(Category).count()
    total_pages = (total_categories + limit - 1) // limit
    return {
        "categories": categories,
        "total_pages": total_pages,
        "total_categories": total_categories
    }

@router.get("/{category_id}", response_model=CategoryResponse)
def read_category(category_id: int, db: Session = Depends(get_db)):
    category = db.query(Category).options(joinedload(Category.books)).filter(Category.id == category_id).first()
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    return category

@router.put("/{category_id}", response_model=CategoryResponse)
def update_category(category_id: int, category_update: CategoryUpdate, db: Session = Depends(get_db)):
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
    return category

@router.delete("/{category_id}")
def delete_category(category_id: int, db: Session = Depends(get_db)):
    category = db.query(Category).filter(Category.id == category_id).first()
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    db.delete(category)
    db.commit()
    return {"message": "Category deleted"}
