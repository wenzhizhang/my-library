from fastapi import APIRouter, Request, Depends
from sqlalchemy.orm import Session
from sqlalchemy import func
from datetime import datetime

from database import get_stats_db
from models.stats import VisitLog

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


@router.get("/visit")
@router.get("/visits")
def stats_visits(limit: int = 50, skip: int = 0, db: Session = Depends(get_stats_db)):
    """Return recent page-views with IP, time, and path."""
    visits = (
        db.query(VisitLog)
        .order_by(VisitLog.timestamp.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )
    return [
        {
            "ip": v.ip,
            "path": v.path,
            "timestamp": v.timestamp.isoformat() if v.timestamp else None,
        }
        for v in visits
    ]
