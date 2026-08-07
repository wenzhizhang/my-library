from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from sqlalchemy import func, case
from typing import List, Optional

from models import ReadingPlan, Book, reading_plan_items
from models.book import apply_book_sort, apply_book_q
from schemas.reading_plan import (
    ReadingPlanCreation,
    ReadingPlanUpdate,
    ReadingPlanResponse,
    ReadingPlanSummary,
    ReadingPlanListResponse,
    AddBookToPlan,
    BatchAddBooks,
)
from database import get_db
from serializers import serialize_book

router = APIRouter(prefix="/api/reading-plans", tags=["reading-plans"])


@router.post("/", response_model=ReadingPlanResponse)
def create_reading_plan(
    plan: ReadingPlanCreation, db: Session = Depends(get_db)
):
    db_plan = ReadingPlan(**plan.model_dump())
    db.add(db_plan)
    db.commit()
    db.refresh(db_plan)
    return db_plan


@router.get("/", response_model=ReadingPlanListResponse)
def read_reading_plans(
    page: int = 1,
    limit: int = 10,
    sort_by: str = "name",
    q: Optional[str] = None,
    db: Session = Depends(get_db),
):
    offset = (page - 1) * limit
    query = db.query(ReadingPlan).options(selectinload(ReadingPlan.books))

    if sort_by == "name":
        query = query.order_by(ReadingPlan.name)
    elif sort_by == "created_at":
        query = query.order_by(ReadingPlan.created_at.desc())
    elif sort_by == "start_date":
        query = query.order_by(ReadingPlan.start_date)
    elif sort_by == "id":
        query = query.order_by(ReadingPlan.id)
    if q:
        query = query.filter(ReadingPlan.name.ilike(f"%{q}%"))

    plans = query.offset(offset).limit(limit).all()
    total_plans = query.count()
    total_pages = (total_plans + limit - 1) // limit

    plans_data = []
    for p in plans:
        # Compute progress from the loaded books relationship
        total_volumes = sum(b.book_count or 1 for b in p.books)
        read_volumes = sum(
            b.book_count or 1 for b in p.books if b.read_state == "read"
        )
        progress = round(read_volumes / total_volumes * 100, 1) if total_volumes > 0 else 0.0

        plans_data.append(
            {
                "id": p.id,
                "name": p.name,
                "intro": p.intro,
                "start_date": p.start_date,
                "end_date": p.end_date,
                "created_at": p.created_at.isoformat() if p.created_at else None,
                "total_books": len(p.books),
                "progress": progress,
            }
        )

    return {
        "reading_plans": plans_data,
        "total_pages": total_pages,
        "total_plans": total_plans,
    }


@router.get("/{plan_id}", response_model=ReadingPlanResponse)
def read_reading_plan(plan_id: int, db: Session = Depends(get_db)):
    plan = (
        db.query(ReadingPlan)
        .options(
            joinedload(ReadingPlan.books).joinedload(Book.authors)
        )
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")
    plan.books = [b for b in plan.books if not b.in_wish]
    return plan


@router.get("/{plan_id}/books")
def read_plan_books(plan_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    plan = db.query(ReadingPlan).filter(ReadingPlan.id == plan_id).first()
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.reading_plans.any(ReadingPlan.id == plan_id),
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


@router.put("/{plan_id}", response_model=ReadingPlanResponse)
def update_reading_plan(
    plan_id: int,
    plan_update: ReadingPlanUpdate,
    db: Session = Depends(get_db),
):
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")
    for key, value in plan_update.model_dump(exclude_unset=True).items():
        setattr(plan, key, value)
    db.commit()
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    return plan


@router.delete("/{plan_id}")
def delete_reading_plan(plan_id: int, db: Session = Depends(get_db)):
    plan = (
        db.query(ReadingPlan)
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")
    # Explicitly delete association rows first — avoids any ORM cascade risk
    db.execute(
        reading_plan_items.delete().where(
            reading_plan_items.c.plan_id == plan_id
        )
    )
    db.delete(plan)
    db.commit()
    return {"message": "Reading plan deleted"}


@router.post("/{plan_id}/books/batch", response_model=ReadingPlanResponse)
def batch_add_books_to_plan(
    plan_id: int,
    body: BatchAddBooks,
    db: Session = Depends(get_db),
):
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")

    books = db.query(Book).filter(Book.id.in_(body.book_ids)).all()
    found_ids = {b.id for b in books}
    missing = [bid for bid in body.book_ids if bid not in found_ids]
    if missing:
        raise HTTPException(status_code=400, detail=f"Books not found: {missing}")

    existing_ids = set(
        r.book_id
        for r in db.query(reading_plan_items.c.book_id)
        .filter(
            reading_plan_items.c.plan_id == plan_id,
            reading_plan_items.c.book_id.in_(body.book_ids),
        )
        .all()
    )
    duplicates = [bid for bid in body.book_ids if bid in existing_ids]
    if duplicates:
        raise HTTPException(status_code=400, detail=f"Books already in plan: {duplicates}")

    for book in books:
        plan.books.append(book)
    db.commit()
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    return plan


@router.post("/{plan_id}/books", response_model=ReadingPlanResponse)
def add_book_to_plan(
    plan_id: int,
    body: AddBookToPlan,
    db: Session = Depends(get_db),
):
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")

    book = db.query(Book).filter(Book.id == body.book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    existing = (
        db.query(reading_plan_items)
        .filter(
            reading_plan_items.c.plan_id == plan_id,
            reading_plan_items.c.book_id == body.book_id,
        )
        .first()
    )
    if existing:
        raise HTTPException(status_code=400, detail="Book already in plan")

    plan.books.append(book)
    db.commit()
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    return plan


@router.delete("/{plan_id}/books/{book_id}", response_model=ReadingPlanResponse)
def remove_book_from_plan(
    plan_id: int,
    book_id: int,
    db: Session = Depends(get_db),
):
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Reading plan not found")

    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    existing = (
        db.query(reading_plan_items)
        .filter(
            reading_plan_items.c.plan_id == plan_id,
            reading_plan_items.c.book_id == book_id,
        )
        .first()
    )
    if not existing:
        raise HTTPException(status_code=400, detail="Book not in plan")

    plan.books.remove(book)
    db.commit()
    plan = (
        db.query(ReadingPlan)
        .options(joinedload(ReadingPlan.books).joinedload(Book.authors))
        .filter(ReadingPlan.id == plan_id)
        .first()
    )
    return plan
