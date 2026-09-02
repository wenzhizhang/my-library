from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from typing import List, Optional

from models import BookSeries, Book
from models.book import apply_book_sort, apply_book_q
from schemas.series import BookSeriesCreation, BookSeriesUpdate, BookSeriesResponse

from auth import get_current_user_id
from services.sync_to_root import sync_series
from database import get_db
from serializers import serialize_book

router = APIRouter(prefix="/api/series", tags=["series"])

@router.post("/", response_model=BookSeriesResponse)
def create_series(series: BookSeriesCreation, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    db_series = BookSeries(**series.dict())
    db.add(db_series)
    db.commit()
    db.refresh(db_series)
    sync_series(db_series)
    return db_series

@router.get("/")
def read_series(page: int = 1, limit: int = 10, q: Optional[str] = None, sort_by: str = 'weight', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(BookSeries)
    if q:
        query = query.filter(BookSeries.name.ilike(f'%{q}%'))
    if sort_by == 'name':
        query = query.order_by(BookSeries.name)
    elif sort_by == 'created_at':
        query = query.order_by(BookSeries.created_at)
    elif sort_by == 'weight':
        query = query.order_by(BookSeries.weight.desc(), BookSeries.name.asc(), BookSeries.id.asc())
    series = query.offset(offset).limit(limit).all()
    total_series = query.count()
    total_pages = (total_series + limit - 1) // limit

    series_data = []
    for s in series:
        series_data.append({
            "id": s.id,
            "name": s.name,
            "intro": s.intro,
            "weight": s.weight,
        })

    return {
        "series": series_data,
        "total_pages": total_pages,
        "total_series": total_series
    }

@router.get("/{series_id}", response_model=BookSeriesResponse)
def read_series_item(series_id: int, db: Session = Depends(get_db)):
    series = db.query(BookSeries).options(joinedload(BookSeries.books).joinedload(Book.authors)).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    series.books = [b for b in series.books if not b.in_wish]
    return series

@router.get("/{series_id}/books")
def read_series_books(series_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    series = db.query(BookSeries).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.book_series_id == series_id,
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

@router.put("/{series_id}", response_model=BookSeriesResponse)
def update_series(series_id: int, series_update: BookSeriesUpdate, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    series = db.query(BookSeries).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    for key, value in series_update.dict(exclude_unset=True).items():
        setattr(series, key, value)
    db.commit()
    db.refresh(series)
    sync_series(series)
    return series

@router.delete("/{series_id}")
def delete_series(series_id: int, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    series = db.query(BookSeries).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    db.delete(series)
    db.commit()
    return {"message": "Series deleted"}
