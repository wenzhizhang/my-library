from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import Optional

from models import Author, Book
from schemas.author import (
    AuthorCreation, AuthorUpdate, AuthorResponse,
    NATIONS, DYNASTIES,
)
from database import get_db
from auth import get_current_user_id

router = APIRouter(prefix="/api/authors", tags=["authors"])


# ── Public endpoints ──────────────────────────────────────────

@router.get("/")
def read_authors(
    page: int = 1,
    limit: int = 10,
    sort_by: str = "name",
    db: Session = Depends(get_db),
):
    offset = (page - 1) * limit
    query = db.query(Author)
    if sort_by == "name":
        query = query.order_by(Author.name)
    elif sort_by == "created_at":
        query = query.order_by(Author.created_at)
    authors = query.offset(offset).limit(limit).all()
    total_authors = db.query(Author).count()
    total_pages = (total_authors + limit - 1) // limit

    authors_data = []
    for a in authors:
        authors_data.append({
            "id": a.id,
            "name": a.name,
            "name_cn": a.name_cn,
            "nation": a.nation,
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
    return author


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
