from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from typing import List, Optional

from models import Bookshelf, Book
from models.book import apply_book_sort, apply_book_q
from schemas.bookshelf import BookshelfCreation, BookshelfUpdate, BookshelfResponse
from database import get_db
from serializers import serialize_book
from auth import get_current_user_id

router = APIRouter(prefix="/api/bookshelves", tags=["bookshelves"])

@router.post("/", response_model=BookshelfResponse)
def create_bookshelf(bookshelf: BookshelfCreation, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    db_bookshelf = Bookshelf(**bookshelf.model_dump())
    db.add(db_bookshelf)
    db.commit()
    db.refresh(db_bookshelf)
    return db_bookshelf

@router.get("/")
def read_bookshelves(page: int = 1, limit: int = 10, sort_by: str = 'name', q: Optional[str] = None, db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Bookshelf)
    if q:
        query = query.filter(Bookshelf.name.ilike(f'%{q}%'))
    if sort_by == 'name':
        query = query.order_by(Bookshelf.name)
    elif sort_by == 'created_at':
        query = query.order_by(Bookshelf.created_at)
    bookshelves = query.offset(offset).limit(limit).all()
    total_bookshelves = query.count()
    total_pages = (total_bookshelves + limit - 1) // limit

    bookshelves_data = []
    for bs in bookshelves:
        bookshelves_data.append({
            "id": bs.id,
            "name": bs.name,
            "intro": bs.intro,
        })

    return {
        "bookshelves": bookshelves_data,
        "total_pages": total_pages,
        "total_bookshelves": total_bookshelves
    }

@router.get("/{bookshelf_id}", response_model=BookshelfResponse)
def read_bookshelf(bookshelf_id: int, db: Session = Depends(get_db)):
    bookshelf = db.query(Bookshelf).options(joinedload(Bookshelf.books).joinedload(Book.authors)).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    bookshelf.books = [b for b in bookshelf.books if not b.in_wish]
    return bookshelf

@router.get("/{bookshelf_id}/books")
def read_bookshelf_books(bookshelf_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    bookshelf = db.query(Bookshelf).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.bookshelf_id == bookshelf_id,
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

@router.put("/{bookshelf_id}", response_model=BookshelfResponse)
def update_bookshelf(bookshelf_id: int, bookshelf_update: BookshelfUpdate, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    bookshelf = db.query(Bookshelf).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    for key, value in bookshelf_update.dict(exclude_unset=True).items():
        setattr(bookshelf, key, value)
    db.commit()
    db.refresh(bookshelf)
    return bookshelf

@router.delete("/{bookshelf_id}")
def delete_bookshelf(bookshelf_id: int, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    bookshelf = db.query(Bookshelf).filter(Bookshelf.id == bookshelf_id).first()
    if bookshelf is None:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    db.delete(bookshelf)
    db.commit()
    return {"message": "Bookshelf deleted"}
