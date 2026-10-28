from fastapi import APIRouter, Request, Depends
from sqlalchemy.orm import Session
from sqlalchemy import func
from datetime import datetime
from database import get_stats_db, get_db

from models.stats import VisitLog
from services.library_stats import build_book_stats

router = APIRouter(prefix="/api/stats", tags=["stats"])


@router.post("/page-view")
def log_page_view(request: Request, db: Session = Depends(get_stats_db)):
    """Called by frontend on each page navigation. Logs IP + path + time."""
    client_ip = request.client.host if request.client else "unknown"
    db.add(VisitLog(ip=client_ip, path=request.headers.get("referer", "/"), timestamp=datetime.now()))
    db.commit()
    total = db.query(func.count(VisitLog.id)).scalar() or 0
    return {"total_visits": total}


@router.get("/summary")
def stats_summary(db: Session = Depends(get_stats_db)):
    """Return total page-view count."""
    total = db.query(func.count(VisitLog.id)).scalar() or 0
    return {"total_visits": total}



@router.get("/visits")
def stats_visits(limit: int = None, skip: int = 0, db: Session = Depends(get_stats_db)):
    """Return page-views with IP, time, and path. Omit limit to return all records."""
    query = (
        db.query(VisitLog)
        .order_by(VisitLog.timestamp.desc())
        .offset(skip)
    )
    if limit:
        query = query.limit(limit)
    visits = query.all()
    return [
        {
            "ip": v.ip,
            "path": v.path,
            "timestamp": v.timestamp.isoformat() if v.timestamp else None,
        }
        for v in visits
    ]


@router.get("/books")
def book_stats(db: Session = Depends(get_db)):
    """Return comprehensive book library statistics."""
    return build_book_stats(db)
