from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import List

from models import Bookshelf
from schemas.bookshelf import BookshelfCreation, BookshelfUpdate, BookshelfResponse
from database import get_db

router = APIRouter(prefix="/api/bookshelves", tags=["bookshelves"])

@router.post("/", response_model=BookshelfResponse)
def create_bookshelf(bookshelf: BookshelfCreation, db: Session = Depends(get_db)):
    db_bookshelf = Bookshelf(**bookshelf.model_dump())
    db.add(db_bookshelf)
    db.commit()
    db.refresh(db_bookshelf)
    return db_bookshelf

@router.get("/")
def read_bookshelves(page: int = 1, limit: int = 10, sort_by: str = 'name', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Bookshelf)
    if sort_by == 'name':
        query = query.order_by(Bookshelf.name)
    elif sort_by == 'created_at':
        query = query.order_by(Bookshelf.created_at)
    bookshelves = query.offset(offset).limit(limit).all()
    total_bookshelves = db.query(Bookshelf).count()
    total_pages = (total_bookshelves + limit - 1) // limit
    return {
        "bookshelves": bookshelves,
        "total_pages": total_pages,
        "total_bookshelves": total_bookshelves
    }

@router.get("/{bookshelf_id}", response_model=BookshelfResponse)
def read_bookshelf(bookshelf_id: int, db: Session = Depends(get_db)):
    bookshelf = db.query(Bookshelf).options(joinedload(Bookshelf.books)).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    return bookshelf

@router.put("/{bookshelf_id}", response_model=BookshelfResponse)
def update_bookshelf(bookshelf_id: int, bookshelf_update: BookshelfUpdate, db: Session = Depends(get_db)):
    bookshelf = db.query(Bookshelf).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    for key, value in bookshelf_update.dict(exclude_unset=True).items():
        setattr(bookshelf, key, value)
    db.commit()
    db.refresh(bookshelf)
    return bookshelf

@router.delete("/{bookshelf_id}")
def delete_bookshelf(bookshelf_id: int, db: Session = Depends(get_db)):
    bookshelf = db.query(Bookshelf).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    db.delete(bookshelf)
    db.commit()
    return {"message": "Bookshelf deleted"}
