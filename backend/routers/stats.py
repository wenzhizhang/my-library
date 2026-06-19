from fastapi import APIRouter, Request, Depends
from sqlalchemy.orm import Session
from sqlalchemy import case, extract, func
from datetime import datetime
from database import get_stats_db, get_db
from models import Book, Author, Publisher, Category

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


@router.get("/books")
def book_stats(db: Session = Depends(get_db)):
    """Return comprehensive book library statistics."""

    total_books = db.query(func.count(Book.id)).scalar() or 0
    total_authors = db.query(func.count(Author.id)).scalar() or 0
    total_publishers = db.query(func.count(Publisher.id)).scalar() or 0
    total_categories = db.query(func.count(Category.id)).scalar() or 0

    # Books by read state
    by_read_state = (
        db.query(Book.read_state, func.count(Book.id))
        .group_by(Book.read_state)
        .all()
    )
    read_state_data = [{"name": s or "unknown", "count": c} for s, c in by_read_state]

    # Books by category
    by_category = (
        db.query(Category.name, func.count(Book.id))
        .join(Book, Book.category_id == Category.id, isouter=True)
        .group_by(Category.name)
        .order_by(func.count(Book.id).desc())
        .all()
    )
    category_data = [{"name": n or "Uncategorized", "count": c} for n, c in by_category]

    # Books by binding type
    by_binding = (
        db.query(Book.binding_type, func.count(Book.id))
        .group_by(Book.binding_type)
        .all()
    )
    binding_data = [{"name": b or "unknown", "count": c} for b, c in by_binding]

    # Books by language
    by_language = (
        db.query(Book.language, func.count(Book.id))
        .group_by(Book.language)
        .order_by(func.count(Book.id).desc())
        .all()
    )
    language_data = [{"name": l or "unknown", "count": c} for l, c in by_language]

    # Top authors by book count
    top_authors = (
        db.query(Author.name_cn, Author.name, func.count(Book.id))
        .join(Author.books)
        .group_by(Author.id)
        .order_by(func.count(Book.id).desc())
        .all()
    )
    author_data = [{"name": cn or n, "count": c} for cn, n, c in top_authors]

    # Top publishers by book count
    top_publishers = (
        db.query(Publisher.name, func.count(Book.id))
        .join(Book, Book.publisher_id == Publisher.id, isouter=True)
        .group_by(Publisher.name)
        .order_by(func.count(Book.id).desc())
        .all()
    )
    publisher_data = [{"name": n or "Unknown", "count": c} for n, c in top_publishers]

    # Books added per month (all months)
    books_by_month = (
        db.query(
            extract("year", Book.created_at).label("year"),
            extract("month", Book.created_at).label("month"),
            func.count(Book.id),
        )
        .filter(Book.created_at.isnot(None))
        .group_by("year", "month")
        .order_by("year", "month")
        .all()
    )
    timeline_months = [
        {"label": f"{int(y)}-{int(m):02d}", "year": int(y), "month": int(m), "count": c}
        for y, m, c in books_by_month
    ]

    # Books added per year
    books_by_year = (
        db.query(
            extract("year", Book.created_at).label("year"),
            func.count(Book.id),
        )
        .filter(Book.created_at.isnot(None))
        .group_by("year")
        .order_by("year")
        .all()
    )
    timeline_years = [
        {"label": str(int(y)), "year": int(y), "count": c}
        for y, c in books_by_year
    ]

    # Books purchased per month with total price
    purchase_by_month = (
        db.query(
            extract("year", Book.purchase_date).label("year"),
            extract("month", Book.purchase_date).label("month"),
            func.count(Book.id),
            func.coalesce(func.sum(Book.purchase_price), 0),
        )
        .filter(Book.purchase_date.isnot(None))
        .group_by("year", "month")
        .order_by("year", "month")
        .all()
    )
    purchase_months = [
        {"label": f"{int(y)}-{int(m):02d}", "year": int(y), "month": int(m), "count": c, "price": round(float(p), 2)}
        for y, m, c, p in purchase_by_month
    ]

    # Purchases per year with total price
    purchase_by_year = (
        db.query(
            extract("year", Book.purchase_date).label("year"),
            func.count(Book.id),
            func.coalesce(func.sum(Book.purchase_price), 0),
        )
        .filter(Book.purchase_date.isnot(None))
        .group_by("year")
        .order_by("year")
        .all()
    )
    purchase_years = [
        {"label": str(int(y)), "year": int(y), "count": c, "price": round(float(p), 2)}
        for y, c, p in purchase_by_year
    ]

    # Price stats
    avg_price = db.query(func.avg(Book.price)).filter(Book.price > 0).scalar() or 0
    avg_purchase_price = db.query(func.avg(Book.purchase_price)).filter(Book.purchase_price > 0).scalar() or 0
    total_spent = db.query(func.sum(Book.purchase_price)).filter(Book.purchase_price > 0).scalar() or 0

    # Douban score distribution
    by_score = (
        db.query(
            case(
                (Book.douban_score >= 9.0, "9.0+"),
                (Book.douban_score >= 8.0, "8.0-8.9"),
                (Book.douban_score >= 7.0, "7.0-7.9"),
                (Book.douban_score >= 6.0, "6.0-6.9"),
                else_="< 6.0",
            ).label("range"),
            func.count(Book.id),
        )
        .filter(Book.douban_score.isnot(None))
        .group_by("range")
        .order_by("range")
        .all()
    )
    score_data = [{"name": r, "count": c} for r, c in by_score]

    # Books by compose type
    by_compose = (
        db.query(Book.compose_type, func.count(Book.id))
        .group_by(Book.compose_type)
        .all()
    )
    compose_data = [{"name": ct or "unknown", "count": c} for ct, c in by_compose]
    return {
        "overview": {
            "total_books": total_books,
            "total_authors": total_authors,
            "total_publishers": total_publishers,
            "total_categories": total_categories,
            "avg_price": round(float(avg_price), 2),
            "avg_purchase_price": round(float(avg_purchase_price), 2),
            "total_spent": round(float(total_spent), 2),
        },
        "by_read_state": read_state_data,
        "by_category": category_data,
        "by_binding": binding_data,
        "by_language": language_data,
        "top_authors": author_data,
        "top_publishers": publisher_data,
        "timeline_months": timeline_months,
        "timeline_years": timeline_years,
        "purchase_months": purchase_months,
        "purchase_years": purchase_years,
        "by_score": score_data,
        "by_compose": compose_data,
    }
