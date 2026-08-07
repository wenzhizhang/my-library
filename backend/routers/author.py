from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from sqlalchemy import or_
from typing import Optional

from models import Author, Book
from models.book import apply_book_sort, apply_book_q
from schemas.author import (
    AuthorCreation, AuthorUpdate, AuthorResponse,
    NATIONS, DYNASTIES,
)
from services.sync_to_root import sync_author
from database import get_db
from serializers import serialize_book
from auth import get_current_user_id

router = APIRouter(prefix="/api/authors", tags=["authors"])


# ── Public endpoints ──────────────────────────────────────────

@router.get("/")
def read_authors(
    page: int = 1,
    limit: int = 10,
    sort_by: str = "name",
    q: Optional[str] = None,
    db: Session = Depends(get_db),
):
    offset = (page - 1) * limit
    query = db.query(Author)
    if sort_by == "name":
        query = query.order_by(Author.name)
    elif sort_by == "created_at":
        query = query.order_by(Author.created_at)
    if q:
        query = query.filter(or_(Author.name.ilike(f'%{q}%'), Author.name_cn.ilike(f'%{q}%')))
    authors = query.offset(offset).limit(limit).all()
    total_authors = query.count()
    total_pages = (total_authors + limit - 1) // limit

    authors_data = []
    for a in authors:
        authors_data.append({
            "id": a.id,
            "name": a.name,
            "name_cn": a.name_cn,
            "nation": a.nation or "无",
            "dynasty": a.dynasty,
            "intro": a.intro,
            "photo": a.photo,
        })

    return {
        "authors": authors_data,
        "total_pages": total_pages,
        "total_authors": total_authors,
    }


@router.get("/nations")
def get_nations():
    return {"nations": NATIONS}


@router.get("/dynasties")
def get_dynasties():
    return {"dynasties": DYNASTIES}


@router.get("/{author_id}", response_model=AuthorResponse)
def read_author(author_id: int, db: Session = Depends(get_db)):
    author = (
        db.query(Author)
        .options(joinedload(Author.books).joinedload(Book.authors))
        .filter(Author.id == author_id)
        .first()
    )
    if author is None:
        raise HTTPException(status_code=404, detail="Author not found")
    author.books = [b for b in author.books if not b.in_wish]
    return author


@router.get("/{author_id}/books")
def read_author_books(author_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    author = db.query(Author).filter(Author.id == author_id).first()
    if author is None:
        raise HTTPException(status_code=404, detail="Author not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.authors.any(Author.id == author_id),
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


# ── Auth-required endpoints ───────────────────────────────────

@router.post("/", response_model=AuthorResponse)
def create_author(
    author: AuthorCreation,
    db: Session = Depends(get_db),
    user_id: Optional[int] = Depends(get_current_user_id),
):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    db_author = Author(**author.model_dump())
    db.add(db_author)
    db.commit()
    db.refresh(db_author)
    sync_author(db_author)
    return db_author


@router.put("/{author_id}", response_model=AuthorResponse)
def update_author(
    author_id: int,
    author_update: AuthorUpdate,
    db: Session = Depends(get_db),
    user_id: Optional[int] = Depends(get_current_user_id),
):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    author = (
        db.query(Author)
        .options(joinedload(Author.books).joinedload(Book.authors))
        .filter(Author.id == author_id)
        .first()
    )
    if author is None:
        raise HTTPException(status_code=404, detail="Author not found")
    for key, value in author_update.model_dump(exclude_unset=True).items():
        setattr(author, key, value)
    db.commit()
    db.refresh(author)
    sync_author(author)
    return author


@router.delete("/{author_id}")
def delete_author(
    author_id: int,
    db: Session = Depends(get_db),
    user_id: Optional[int] = Depends(get_current_user_id),
):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    author = (
        db.query(Author)
        .options(joinedload(Author.books).joinedload(Book.authors))
        .filter(Author.id == author_id)
        .first()
    )
    if author is None:
        raise HTTPException(status_code=404, detail="Author not found")
    db.delete(author)
    db.commit()
    return {"message": "Author deleted"}
