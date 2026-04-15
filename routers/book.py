from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from models import Book, Author, book_authors
from schemas.book import BookCreation, BookUpdate, BookResponse
from database import get_db

router = APIRouter(prefix="/api/books", tags=["books"])

@router.post("/", response_model=BookResponse)
def create_book(book: BookCreation, db: Session = Depends(get_db)):
    # Handle authors
    authors = db.query(Author).filter(Author.id.in_(book.author_ids)).all()
    
    book_data = book.model_dump(exclude={"author_ids"})
    db_book = Book(**book_data)
    db_book.authors = authors
    
    db.add(db_book)
    db.commit()
    db.refresh(db_book)
    return {
        "id": db_book.id,
        "isbn": db_book.isbn,
        "title_cn": db_book.title_cn,
        "title": db_book.title,
        "author_ids": [author.id for author in db_book.authors],
        "translator": book.translator,
        "publisher_id": db_book.publisher_id,
        "publish_date": db_book.publish_date,
        "brand_id": db_book.brand_id,
        "book_series_id": db_book.book_series_id,
        "binding_type": db_book.binding_type,
        "paper_type": db_book.paper_type,
        "pages": db_book.pages,
        "book_count": db_book.book_count,
        "language": db_book.language,
        "compose_type": db_book.compose_type,
        "price": db_book.price,
        "purchase_price": db_book.purchase_price,
        "purchase_date": db_book.purchase_date,
        "thumb_image": db_book.thumb_image,
        "link": db_book.link,
        "category_id": db_book.category_id,
        "bookshelf_id": db_book.bookshelf_id,
        "read_state": db_book.read_state,
        "catalog": db_book.catalog,
        "introduction": db_book.introduction,
        "summary": db_book.summary,
        "registered": db_book.registered,
        "edition": db_book.edition,
        "printing_info": db_book.printing_info,
        "printed_number": db_book.printed_number,
        "douban_score": db_book.douban_score,
        "purchase_store": db_book.purchase_store,
        "tags": db_book.tags or [],
    }

@router.get("/")
def read_books(page: int = 1, limit: int = 10, sort_by: str = "title", db: Session = Depends(get_db)):
    offset = (page - 1) * limit

    total_books = db.query(Book).count()
    total_pages = (total_books + limit - 1) // limit

    if sort_by == "title":
        books = db.query(Book).order_by(Book.title).offset(offset).limit(limit).all()
    elif sort_by == "created_at":
        books = db.query(Book).order_by(Book.created_at.desc()).offset(offset).limit(limit).all()
    else:
        books = db.query(Book).offset(offset).limit(limit).all()

    books_data = []
    for book in books:
        book_dict = {
            "id": book.id,
            "isbn": book.isbn,
            "title_cn": book.title_cn,
            "title": book.title,
            "thumb_image": book.thumb_image,
            "authors": [author.name for author in book.authors]
        }
        books_data.append(book_dict)

    return {"books": books_data, "total_pages": total_pages, "total_books": total_books}

@router.get("/{book_id}", response_model=BookResponse)
def read_book(book_id: int, db: Session = Depends(get_db)):
    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    print(book.authors)
    return {
        "id": book.id,
        "isbn": book.isbn,
        "title_cn": book.title_cn,
        "title": book.title,
        "author_ids": [author.id for author in book.authors],
        "translator": book.translator,
        "publisher_id": book.publisher_id,
        "publish_date": book.publish_date,
        "brand_id": book.brand_id,
        "book_series_id": book.book_series_id,
        "binding_type": book.binding_type,
        "paper_type": book.paper_type,
        "pages": book.pages,
        "book_count": book.book_count,
        "language": book.language,
        "compose_type": book.compose_type,
        "price": book.price,
        "purchase_price": book.purchase_price,
        "purchase_date": book.purchase_date,
        "thumb_image": book.thumb_image,
        "link": book.link,
        "category_id": book.category_id,
        "bookshelf_id": book.bookshelf_id,
        "read_state": book.read_state,
        "catalog": book.catalog,
        "introduction": book.introduction,
        "summary": book.summary,
        "registered": book.registered,
        "edition": book.edition,
        "printing_info": book.printing_info,
        "printed_number": book.printed_number,
        "douban_score": book.douban_score,
        "purchase_store": book.purchase_store,
        "tags": book.tags or [],
    }

@router.put("/{book_id}", response_model=BookResponse)
def update_book(book_id: int, book_update: BookUpdate, db: Session = Depends(get_db)):
    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    
    update_data = book_update.model_dump(exclude_unset=True)
    if "author_ids" in update_data:
        authors = db.query(Author).filter(Author.id.in_(update_data["author_ids"])).all()
        book.authors = authors
        del update_data["author_ids"]
    
    for key, value in update_data.items():
        setattr(book, key, value)
    db.commit()
    db.refresh(book)
    return {
        "id": book.id,
        "isbn": book.isbn,
        "title_cn": book.title_cn,
        "title": book.title,
        "author_ids": [author.id for author in book.authors],
        "translator": book.translator,
        "publisher_id": book.publisher_id,
        "publish_date": book.publish_date,
        "brand_id": book.brand_id,
        "book_series_id": book.book_series_id,
        "binding_type": book.binding_type,
        "paper_type": book.paper_type,
        "pages": book.pages,
        "book_count": book.book_count,
        "language": book.language,
        "compose_type": book.compose_type,
        "price": book.price,
        "purchase_price": book.purchase_price,
        "purchase_date": book.purchase_date,
        "thumb_image": book.thumb_image,
        "link": book.link,
        "category_id": book.category_id,
        "bookshelf_id": book.bookshelf_id,
        "read_state": book.read_state,
        "catalog": book.catalog,
        "introduction": book.introduction,
        "summary": book.summary,
        "registered": book.registered,
        "edition": book.edition,
        "printing_info": book.printing_info,
        "printed_number": book.printed_number,
        "douban_score": book.douban_score,
        "purchase_store": book.purchase_store,
        "tags": book.tags or [],
    }

@router.delete("/{book_id}")
def delete_book(book_id: int, db: Session = Depends(get_db)):
    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    db.delete(book)
    db.commit()
    return {"message": "Book deleted"}
