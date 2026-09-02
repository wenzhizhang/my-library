from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload, selectinload
from typing import List, Optional

from models import BookCollection, Book, book_collection_items
from models.book import apply_book_sort, apply_book_q
from schemas.book_collection import (
    BookCollectionCreation,
    BookCollectionUpdate,
    BookCollectionResponse,
    BookCollectionSummary,
    BookCollectionListResponse,
    AddBookToCollection,
    BatchAddBooks,
)
from database import get_db
from serializers import serialize_book
from services.weights import recompute_weights

router = APIRouter(prefix="/api/book-collections", tags=["book-collections"])


@router.post("/", response_model=BookCollectionResponse)
def create_book_collection(
    collection: BookCollectionCreation, db: Session = Depends(get_db)
):
    db_collection = BookCollection(**collection.model_dump())
    db.add(db_collection)
    db.commit()
    db.refresh(db_collection)
    recompute_weights(db)
    return db_collection


@router.get("/", response_model=BookCollectionListResponse)
def read_book_collections(
    page: int = 1,
    limit: int = 10,
    sort_by: str = "weight",
    q: Optional[str] = None,
    db: Session = Depends(get_db),
):
    offset = (page - 1) * limit
    query = db.query(BookCollection).options(selectinload(BookCollection.books))
    if sort_by == "name":
        query = query.order_by(BookCollection.name)
    elif sort_by == "created_at":
        query = query.order_by(BookCollection.created_at.desc())
    elif sort_by == "weight":
        query = query.order_by(BookCollection.weight.desc(), BookCollection.name.asc(), BookCollection.id.asc())
    if q:
        query = query.filter(BookCollection.name.ilike(f"%{q}%"))

    collections = query.offset(offset).limit(limit).all()
    total_collections = query.count()
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
                "weight": c.weight,
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
    collection.books = [b for b in collection.books if not b.in_wish]
    return collection

@router.get("/{collection_id}/books")
def read_collection_books(collection_id: int, page: int = 1, limit: int = 10, sort_by: str = "title", q: Optional[str] = None, db: Session = Depends(get_db)):
    collection = db.query(BookCollection).filter(BookCollection.id == collection_id).first()
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")
    query = db.query(Book).options(joinedload(Book.authors), selectinload(Book.publisher), selectinload(Book.category)).filter(
        Book.collections.any(BookCollection.id == collection_id),
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


@router.put("/{collection_id}", response_model=BookCollectionResponse)
def update_book_collection(
    collection_id: int,
    collection_update: BookCollectionUpdate,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")
    for key, value in collection_update.model_dump(exclude_unset=True).items():
        setattr(collection, key, value)
    db.commit()
    recompute_weights(db)
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
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
    # Explicitly delete association rows first — avoids any ORM cascade risk
    db.execute(
        book_collection_items.delete().where(
            book_collection_items.c.collection_id == collection_id
        )
    )
    db.delete(collection)
    db.commit()
    recompute_weights(db)
    return {"message": "Book collection deleted"}




@router.post("/{collection_id}/books/batch", response_model=BookCollectionResponse)
def batch_add_books_to_collection(
    collection_id: int,
    body: BatchAddBooks,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")

    books = db.query(Book).filter(Book.id.in_(body.book_ids)).all()
    found_ids = {b.id for b in books}
    missing = [bid for bid in body.book_ids if bid not in found_ids]
    if missing:
        raise HTTPException(status_code=400, detail=f"Books not found: {missing}")

    existing_ids = set(r.book_id for r in db.query(book_collection_items.c.book_id).filter(book_collection_items.c.collection_id == collection_id, book_collection_items.c.book_id.in_(body.book_ids)).all())
    duplicates = [bid for bid in body.book_ids if bid in existing_ids]
    if duplicates:
        raise HTTPException(status_code=400, detail=f"Books already in collection: {duplicates}")

    for book in books:
        collection.books.append(book)
    db.commit()
    recompute_weights(db)
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    return collection
@router.post("/{collection_id}/books", response_model=BookCollectionResponse)
def add_book_to_collection(
    collection_id: int,
    body: AddBookToCollection,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")

    book = db.query(Book).filter(Book.id == body.book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    existing = db.query(book_collection_items).filter(book_collection_items.c.collection_id == collection_id, book_collection_items.c.book_id == body.book_id).first()
    if existing:
        raise HTTPException(status_code=400, detail="Book already in collection")

    collection.books.append(book)
    db.commit()
    recompute_weights(db)
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    return collection


@router.delete("/{collection_id}/books/{book_id}", response_model=BookCollectionResponse)
def remove_book_from_collection(
    collection_id: int,
    book_id: int,
    db: Session = Depends(get_db),
):
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    if collection is None:
        raise HTTPException(status_code=404, detail="Book collection not found")

    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")

    existing = db.query(book_collection_items).filter(book_collection_items.c.collection_id == collection_id, book_collection_items.c.book_id == book_id).first()
    if not existing:
        raise HTTPException(status_code=400, detail="Book not in collection")

    collection.books.remove(book)
    db.commit()
    recompute_weights(db)
    collection = (
        db.query(BookCollection)
        .options(joinedload(BookCollection.books).joinedload(Book.authors))
        .filter(BookCollection.id == collection_id)
        .first()
    )
    return collection
