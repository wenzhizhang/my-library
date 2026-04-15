from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import List

from models import Publisher, Brand
from schemas.publisher import PublisherCreation, PublisherUpdate, PublisherResponse, BrandCreation, BrandUpdate, BrandResponse
from database import get_db

publisher_router = APIRouter(prefix="/api/publishers", tags=["publishers"])

@publisher_router.post("/", response_model=PublisherResponse)
def create_publisher(publisher: PublisherCreation, db: Session = Depends(get_db)):
    db_publisher = Publisher(**publisher.dict())
    db.add(db_publisher)
    db.commit()
    db.refresh(db_publisher)
    return db_publisher

@publisher_router.get("/")
def read_publishers(page: int = 1, limit: int = 10, sort_by: str = 'name', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Publisher)
    if sort_by == 'name':
        query = query.order_by(Publisher.name)
    elif sort_by == 'created_at':
        query = query.order_by(Publisher.created_at)
    publishers = query.offset(offset).limit(limit).all()
    total_publishers = db.query(Publisher).count()
    total_pages = (total_publishers + limit - 1) // limit
    return {
        "publishers": publishers,
        "total_pages": total_pages,
        "total_publishers": total_publishers
    }

@publisher_router.get("/{publisher_id}", response_model=PublisherResponse)
def read_publisher(publisher_id: int, db: Session = Depends(get_db)):
    publisher = db.query(Publisher).options(joinedload(Publisher.books)).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    return publisher

@publisher_router.put("/{publisher_id}", response_model=PublisherResponse)
def update_publisher(publisher_id: int, publisher_update: PublisherUpdate, db: Session = Depends(get_db)):
    publisher = db.query(Publisher).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    for key, value in publisher_update.dict(exclude_unset=True).items():
        setattr(publisher, key, value)
    db.commit()
    db.refresh(publisher)
    return publisher

@publisher_router.delete("/{publisher_id}")
def delete_publisher(publisher_id: int, db: Session = Depends(get_db)):
    publisher = db.query(Publisher).filter(Publisher.id == publisher_id).first()
    if publisher is None:
        raise HTTPException(status_code=404, detail="Publisher not found")
    db.delete(publisher)
    db.commit()
    return {"message": "Publisher deleted"}

brand_router = APIRouter(prefix="/api/brands", tags=["brands"])

@brand_router.post("/", response_model=BrandResponse)
def create_brand(brand: BrandCreation, db: Session = Depends(get_db)):
    db_brand = Brand(**brand.dict())
    db.add(db_brand)
    db.commit()
    db.refresh(db_brand)
    return db_brand

@brand_router.get("/")
def read_brands(page: int = 1, limit: int = 10, sort_by: str = 'name', db: Session = Depends(get_db)):
    offset = (page - 1) * limit
    query = db.query(Brand)
    if sort_by == 'name':
        query = query.order_by(Brand.name)
    elif sort_by == 'created_at':
        query = query.order_by(Brand.created_at)
    brands = query.offset(offset).limit(limit).all()
    total_brands = db.query(Brand).count()
    total_pages = (total_brands + limit - 1) // limit
    return {
        "brands": brands,
        "total_pages": total_pages,
        "total_brands": total_brands
    }

@brand_router.get("/{brand_id}", response_model=BrandResponse)
def read_brand(brand_id: int, db: Session = Depends(get_db)):
    brand = db.query(Brand).options(joinedload(Brand.books)).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    return brand

@brand_router.put("/{brand_id}", response_model=BrandResponse)
def update_brand(brand_id: int, brand_update: BrandUpdate, db: Session = Depends(get_db)):
    brand = db.query(Brand).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    for key, value in brand_update.dict(exclude_unset=True).items():
        setattr(brand, key, value)
    db.commit()
    db.refresh(brand)
    return brand

@brand_router.delete("/{brand_id}")
def delete_brand(brand_id: int, db: Session = Depends(get_db)):
    brand = db.query(Brand).filter(Brand.id == brand_id).first()
    if brand is None:
        raise HTTPException(status_code=404, detail="Brand not found")
    db.delete(brand)
    db.commit()
    return {"message": "Brand deleted"}
