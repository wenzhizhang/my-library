from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload
from typing import List

from models import BookCollection, Book
from schemas.book_collection import (
    BookCollectionCreation,
    BookCollectionUpdate,
    BookCollectionResponse,
    AddBookToCollection,
    BatchAddBooks,
)
from database import get_db

router = APIRouter(prefix="/api/book-collections", tags=["book-collections"])


@router.post("/", response_model=BookCollectionResponse)
def create_book_collection(
    collection: BookCollectionCreation, db: Session = Depends(get_db)
):
    db_collection = BookCollection(**collection.model_dump())
    db.add(db_collection)
    db.commit()
    db.refresh(db_collection)
    return db_collection


@router.get("/")
def read_book_collections(
    page: int = 1,
    limit: int = 10,
    sort_by: str = "name",
    db: Session = Depends(get_db),
):
    offset = (page - 1) * limit
    query = db.query(BookCollection)
    if sort_by == "name":
        query = query.order_by(BookCollection.name)
    elif sort_by == "created_at":
        query = query.order_by(BookCollection.created_at.desc())
    collections = query.offset(offset).limit(limit).all()
    total_collections = db.query(BookCollection).count()
    total_pages = (total_collections + limit - 1) // limit

    collections_data = []
    for c in collections:
        collections_data.append(
            {
                "id": c.id,
                "name": c.name,
                "intro": c.intro,
                "created_at": c.created_at.isoformat() if c.created_at else None,
                "total_books": len(c.books),
            }
        )

    return {
        "book_collections": collections_data,
        "total_pages": total_pages,
        "total_collections": total_collections,
    }


@router.get("/{collection_id}", response_model=BookCollectionResponse)
def read_book_collection(collection_id: int, db: Session = Depends(get_db)):
    collection = (
        db.query(BookCollection)
        .options(
            joinedload(BookCollection.books).joinedload(Book.authors)
        )
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")
    return collection


@router.put("/{collection_id}", response_model=BookCollectionResponse)
def update_book_collection(
    collection_id: int,
    collection_update: BookCollectionUpdate,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")
    for key, value in collection_update.model_dump(exclude_unset=True).items():
        setattr(collection, key, value)
    db.commit()
    db.refresh(collection)
    return collection


@router.delete("/{collection_id}")
def delete_book_collection(collection_id: int, db: Session = Depends(get_db)):
    collection = (
        db.query(BookCollection)
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")
    db.delete(collection)
    db.commit()
    return {"message": "Book collection deleted"}




@router.post("/{collection_id}/books/batch", response_model=BookCollectionResponse)
def batch_add_books_to_collection(
    collection_id: int,
    body: BatchAddBooks,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")

    books = db.query(Book).filter(Book.id.in_(body.book_ids)).all()
    found_ids = {b.id for b in books}
    missing = [bid for bid in body.book_ids if bid not in found_ids]
    if missing:
        raise HTTPException(status_code=404, detail=f"Books not found: {missing}")

    existing_ids = {b.id for b in collection.books}
    duplicates = [bid for bid in body.book_ids if bid in existing_ids]
    if duplicates:
        raise HTTPException(status_code=400, detail=f"Books already in collection: {duplicates}")

    for book in books:
        collection.books.append(book)
    db.commit()
    db.refresh(collection)
    return collection
@router.post("/{collection_id}/books", response_model=BookCollectionResponse)
def add_book_to_collection(
    collection_id: int,
    body: AddBookToCollection,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")

    book = db.query(Book).filter(Book.id == body.book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    if book in collection.books:
        raise HTTPException(status_code=400, detail="Book already in collection")

    collection.books.append(book)
    db.commit()
    db.refresh(collection)
    return collection


@router.delete("/{collection_id}/books/{book_id}", response_model=BookCollectionResponse)
def remove_book_from_collection(
    collection_id: int,
    book_id: int,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")

    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    if book not in collection.books:
        raise HTTPException(status_code=400, detail="Book not in collection")

    collection.books.remove(book)
    db.commit()
    db.refresh(collection)
    return collection
