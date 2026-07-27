from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload, selectinload
from typing import List, Optional

from models import Book, Author, book_authors
from models.book import BookSearchStrategy
from schemas.book import BookCreation, BookUpdate, BookResponse, FilterParams
from database import get_db
from services.sync_to_root import sync_book as sync_book_to_root, sync_book_author as sync_book_author_to_root
from rag.pipeline import sync_book, remove_book

router = APIRouter(prefix="/api/books", tags=["books"])


@router.get("/search", response_model=List[BookResponse])
def search_books(filter_params: FilterParams = Depends(), db: Session = Depends(get_db)):
    # query = db.query(Book).options(joinedload(Book.authors))

    stmt = select(Book).options(selectinload(Book.authors)).where(Book.in_wish == False)

    filters_dict = filter_params.model_dump(exclude_unset=True)
    stmt = BookSearchStrategy.apply_filters(stmt, filters_dict)
    print(stmt)

    # return query.all()
    result = db.execute(stmt).scalars().all()
    return result


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
    sync_book_to_root(db_book)
    for author in authors:
        sync_book_author_to_root(db_book.id, author.id)
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
def read_books(page: int = 1, limit: int = 10, sort_by: str = "title", filter_params: FilterParams = Depends(), db: Session = Depends(get_db)):
    offset = (page - 1) * limit

    # ✅ 1. 基础 query（加 eager load，避免 N+1）
    query = db.query(Book).options(selectinload(Book.authors)).filter(Book.in_wish == False)

    # ✅ 2. 应用搜索条件
    filters_dict = filter_params.model_dump(exclude_unset=True)
    query = BookSearchStrategy.apply_filters(query, filters_dict)

    # ✅ 3. 计算 total（⚠️ 必须 distinct）
    total_books = query.with_entities(Book.id).distinct().count()
    total_pages = (total_books + limit - 1) // limit

    # ✅ 4. 排序
    if sort_by == "title":
        query = query.order_by(Book.title)
    elif sort_by == "created_at":
        query = query.order_by(Book.created_at.desc())
    else:
        query = query.order_by(Book.id)

    # ✅ 5. 分页
    books = query.offset(offset).limit(limit).all()

    # ✅ 6. 序列化（你原来的逻辑）
    books_data = []
    for book in books:
        books_data.append({
            "id": book.id,
            "isbn": book.isbn,
            "title_cn": book.title_cn,
            "title": book.title,
            "thumb_image": book.thumb_image,
            "authors": [str(author) for author in book.authors]
        })

    return {
        "books": books_data,
        "total_pages": total_pages,
        "total_books": total_books
    }


@router.get("/titles")
def get_book_titles(db: Session = Depends(get_db)):
    """Return all books as lightweight {id, name, thumb_image, book_count} tuples for dropdowns, excluding wishlist items."""
    rows = db.execute(
        select(Book.id, Book.title_cn, Book.title, Book.thumb_image, Book.book_count)
        .where(Book.in_wish == False)
        .order_by(Book.title)
    ).all()
    return [
        {"id": row[0], "name": row[1] or row[2], "thumb_image": row[3], "book_count": row[4]}
        for row in rows
    ]


@router.get("/{book_id}/similar")
def get_similar_books(book_id: int, limit: int = 5, db: Session = Depends(get_db)):
    """Get similar books based on shared tags."""
    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    
    current_tags = book.tags or []
    if not current_tags:
        return {"book": {"id": book.id, "title": book.title_cn or book.title}, "similar_books": []}
    
    # Find other books (excluding current) with at least one matching tag
    from sqlalchemy import or_
    tag_conditions = [Book.tags.like(f'%"{tag}"%') for tag in current_tags]
    candidates = db.query(Book).options(selectinload(Book.authors)).filter(
        Book.id != book_id,
        Book.tags.isnot(None),
        or_(*tag_conditions)
    ).all()
    
    # Compute similarity score in Python
    current_tag_set = set(current_tags)
    scored = []
    for candidate in candidates:
        candidate_tags = set(candidate.tags or [])
        shared = current_tag_set & candidate_tags
        scored.append((len(shared), candidate))
    
    # Sort by score descending, then take top `limit`
    scored.sort(key=lambda x: x[0], reverse=True)
    top = scored[:limit]
    
    similar_books = []
    for score, b in top:
        similar_books.append({
            "id": b.id,
            "isbn": b.isbn,
            "title_cn": b.title_cn,
            "title": b.title,
            "thumb_image": b.thumb_image,
            "authors": [{"id": a.id, "name": str(a)} for a in (b.authors or [])],
            "tags": b.tags or [],
            "shared_tags": list(current_tag_set & set(b.tags or [])),
            "shared_count": score,
        })
    
    return {
        "book": {"id": book.id, "title": book.title_cn or book.title},
        "similar_books": similar_books,
    }

@router.get("/wishlist")
def read_wishlist(page: int = 1, limit: int = 10, sort_by: str = "created_at", db: Session = Depends(get_db)):
    """Return paginated wishlist books (in_wish=True)."""
    offset = (page - 1) * limit

    query = db.query(Book).options(selectinload(Book.authors)).filter(Book.in_wish == True)

    total_books = query.with_entities(Book.id).distinct().count()
    total_pages = (total_books + limit - 1) // limit if total_books > 0 else 1

    if sort_by == "title":
        query = query.order_by(Book.title)
    elif sort_by == "created_at":
        query = query.order_by(Book.created_at.desc())
    else:
        query = query.order_by(Book.id)

    books = query.offset(offset).limit(limit).all()

    books_data = []
    for book in books:
        books_data.append({
            "id": book.id,
            "isbn": book.isbn,
            "title_cn": book.title_cn,
            "title": book.title,
            "thumb_image": book.thumb_image,
            "authors": [str(author) for author in book.authors]
        })

    return {
        "books": books_data,
        "total_pages": total_pages,
        "total_books": total_books
    }

@router.get("/{book_id}", response_model=BookResponse)
def read_book(book_id: int, db: Session = Depends(get_db)):
    book = db.query(Book).options(joinedload(Book.authors)).filter(Book.id == book_id).one_or_none()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    authors = [{"id": author.id, "name": str(author)} for author in book.authors]
    return {
        "id": book.id,
        "isbn": book.isbn,
        "title_cn": book.title_cn,
        "title": book.title,
        "authors": authors,
        "translator": book.translator,
        "publisher": book.publisher,
        "publish_date": book.publish_date,
        "brand": book.brand,
        "book_series": book.book_series,
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
        "category": book.category,
        "bookshelf": book.bookshelf,
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
        "in_wish": book.in_wish,
    }

@router.put("/{book_id}", response_model=BookResponse)
def update_book(book_id: int, book_update: BookUpdate, db: Session = Depends(get_db)):
    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    
    update_data = book_update.model_dump(exclude_unset=True)
    has_author_update = "author_ids" in update_data
    if has_author_update:
        authors = db.query(Author).filter(Author.id.in_(update_data["author_ids"])).all()
        book.authors = authors
        del update_data["author_ids"]
    
    for key, value in update_data.items():
        setattr(book, key, value)
    db.commit()
    sync_book_to_root(book)
    if has_author_update:
        for author in book.authors:
            sync_book_author_to_root(book.id, author.id)
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
        "in_wish": book.in_wish,
    }

@router.delete("/{book_id}")
def delete_book(book_id: int, db: Session = Depends(get_db)):
    book = db.query(Book).filter(Book.id == book_id).first()
    if book is None:
        raise HTTPException(status_code=404, detail="Book not found")
    # remove_book(db, book_id)  # RAG disabled
    db.delete(book)
    db.commit()
    return {"message": "Book deleted"}
