from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from typing import List, Optional

from models import Publisher, Brand, Book
from models.book import apply_book_sort, apply_book_q
from schemas.publisher import PublisherCreation, PublisherUpdate, PublisherResponse, BrandCreation, BrandUpdate, BrandResponse
from database import get_db
from serializers import serialize_book
from auth import get_current_user_id
from services.sync_to_root import sync_publisher, sync_brand

publisher_router = APIRouter(prefix="/api/publishers", tags=["publishers"])

@publisher_router.post("/", response_model=PublisherResponse)
def create_publisher(publisher: PublisherCreation, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    db_publisher = Publisher(**publisher.dict())
    db.add(db_publisher)
    db.commit()
    db.refresh(db_publisher)
    sync_publisher(db_publisher)
    return db_publisher

@publisher_router.get("/")
def read_publishers(page: int = 1, limit: int = 10, sort_by: str = 'name', q: Optional[str] = None, db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Publisher)
    if sort_by == 'name':
        query = query.order_by(Publisher.name)
    elif sort_by == 'created_at':
        query = query.order_by(Publisher.created_at)
    if q:
        query = query.filter(Publisher.name.ilike(f'%{q}%'))
    publishers = query.offset(offset).limit(limit).all()
    total_publishers = query.count()
    total_pages = (total_publishers + limit - 1) // limit

    publishers_data = []
    for p in publishers:
        publishers_data.append({
            "id": p.id,
            "name": p.name,
            "intro": p.intro,
            "logo": p.logo,
        })

    return {
        "publishers": publishers_data,
        "total_pages": total_pages,
        "total_publishers": total_publishers
    }

@publisher_router.get("/{publisher_id}", response_model=PublisherResponse)
def read_publisher(publisher_id: int, db: Session = Depends(get_db)):
    publisher = db.query(Publisher).options(joinedload(Publisher.books).joinedload(Book.authors)).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    publisher.books = [b for b in publisher.books if not b.in_wish]
    return publisher

@publisher_router.get("/{publisher_id}/books")
def read_publisher_books(publisher_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    publisher = db.query(Publisher).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.publisher_id == publisher_id,
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

@publisher_router.put("/{publisher_id}", response_model=PublisherResponse)
def update_publisher(publisher_id: int, publisher_update: PublisherUpdate, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    publisher = db.query(Publisher).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    for key, value in publisher_update.dict(exclude_unset=True).items():
        setattr(publisher, key, value)
    db.commit()
    db.refresh(publisher)
    sync_publisher(publisher)
    return publisher

@publisher_router.delete("/{publisher_id}")
def delete_publisher(publisher_id: int, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    publisher = db.query(Publisher).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    db.delete(publisher)
    db.commit()
    return {"message": "Publisher deleted"}

brand_router = APIRouter(prefix="/api/brands", tags=["brands"])

@brand_router.post("/", response_model=BrandResponse)
def create_brand(brand: BrandCreation, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    db_brand = Brand(**brand.dict())
    db.add(db_brand)
    db.commit()
    db.refresh(db_brand)
    sync_brand(db_brand)
    return db_brand

@brand_router.get("/")
def read_brands(page: int = 1, limit: int = 10, sort_by: str = 'name', q: Optional[str] = None, db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Brand)
    if sort_by == 'name':
        query = query.order_by(Brand.name)
    elif sort_by == 'created_at':
        query = query.order_by(Brand.created_at)
    if q:
        query = query.filter(Brand.name.ilike(f'%{q}%'))
    brands = query.offset(offset).limit(limit).all()
    total_brands = query.count()
    total_pages = (total_brands + limit - 1) // limit

    brands_data = []
    for b in brands:
        brands_data.append({
            "id": b.id,
            "name": b.name,
            "intro": b.intro,
        })

    return {
        "brands": brands_data,
        "total_pages": total_pages,
        "total_brands": total_brands
    }

@brand_router.get("/{brand_id}", response_model=BrandResponse)
def read_brand(brand_id: int, db: Session = Depends(get_db)):
    brand = db.query(Brand).options(joinedload(Brand.books).joinedload(Book.authors)).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    brand.books = [b for b in brand.books if not b.in_wish]
    return brand

@brand_router.get("/{brand_id}/books")
def read_brand_books(brand_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    brand = db.query(Brand).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.brand_id == brand_id,
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

@brand_router.put("/{brand_id}", response_model=BrandResponse)
def update_brand(brand_id: int, brand_update: BrandUpdate, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    brand = db.query(Brand).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    for key, value in brand_update.dict(exclude_unset=True).items():
        setattr(brand, key, value)
    db.commit()
    db.refresh(brand)
    sync_brand(brand)
    return brand

@brand_router.delete("/{brand_id}")
def delete_brand(brand_id: int, db: Session = Depends(get_db), user_id: Optional[int] = Depends(get_current_user_id)):
    if user_id is None:
        raise HTTPException(status_code=401, detail="Login required")
    brand = db.query(Brand).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    db.delete(brand)
    db.commit()
    return {"message": "Brand deleted"}
