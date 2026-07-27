from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import List, Optional

from models import BookSeries, Book
from schemas.series import BookSeriesCreation, BookSeriesUpdate, BookSeriesResponse

from auth import get_current_user_id
from services.sync_to_root import sync_series
from database import get_db

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
def read_series(page: int = 1, limit: int = 10, q: Optional[str] = None, sort_by: str = 'name', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(BookSeries)
    if q:
        query = query.filter(BookSeries.name.ilike(f'%{q}%'))
    if sort_by == 'name':
        query = query.order_by(BookSeries.name)
    elif sort_by == 'created_at':
        query = query.order_by(BookSeries.created_at)
    series = query.offset(offset).limit(limit).all()
    total_series = query.count()
    total_pages = (total_series + limit - 1) // limit

    series_data = []
    for s in series:
        series_data.append({
            "id": s.id,
            "name": s.name,
            "intro": s.intro,
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
