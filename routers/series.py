from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import List

from models import BookSeries
from schemas.series import BookSeriesCreation, BookSeriesUpdate, BookSeriesResponse
from database import get_db

router = APIRouter(prefix="/api/series", tags=["series"])

@router.post("/", response_model=BookSeriesResponse)
def create_series(series: BookSeriesCreation, db: Session = Depends(get_db)):
    db_series = BookSeries(**series.dict())
    db.add(db_series)
    db.commit()
    db.refresh(db_series)
    return db_series

@router.get("/")
def read_series(page: int = 1, limit: int = 10, sort_by: str = 'name', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(BookSeries)
    if sort_by == 'name':
        query = query.order_by(BookSeries.name)
    elif sort_by == 'created_at':
        query = query.order_by(BookSeries.created_at)
    series = query.offset(offset).limit(limit).all()
    total_series = db.query(BookSeries).count()
    total_pages = (total_series + limit - 1) // limit
    return {
        "series": series,
        "total_pages": total_pages,
        "total_series": total_series
    }

@router.get("/{series_id}", response_model=BookSeriesResponse)
def read_series_item(series_id: int, db: Session = Depends(get_db)):
    series = db.query(BookSeries).options(joinedload(BookSeries.books)).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    return series

@router.put("/{series_id}", response_model=BookSeriesResponse)
def update_series(series_id: int, series_update: BookSeriesUpdate, db: Session = Depends(get_db)):
    series = db.query(BookSeries).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    for key, value in series_update.dict(exclude_unset=True).items():
        setattr(series, key, value)
    db.commit()
    db.refresh(series)
    return series

@router.delete("/{series_id}")
def delete_series(series_id: int, db: Session = Depends(get_db)):
    series = db.query(BookSeries).filter(BookSeries.id == series_id).first()
    if series is None:
        raise HTTPException(status_code=404, detail="Series not found")
    db.delete(series)
    db.commit()
    return {"message": "Series deleted"}
