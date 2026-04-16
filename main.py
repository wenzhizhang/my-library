from fastapi import FastAPI, Request, Depends, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from routers import author_router, book_router, bookshelf_router, category_router, publisher_router, brand_router, series_router
from database import get_db
from sqlalchemy.orm import Session
from models import Author, Book, Bookshelf, Category, Publisher, Brand, BookSeries
from datetime import datetime
from typing import List

app = FastAPI(title="My Library", description="A book library management system")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://111.229.109.204/:3000"],  # 允许所有来源
    allow_credentials=False,  # 修改为 False，或者直接删除这行代码
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files
app.mount("/static", StaticFiles(directory="static"), name="static")

# Include routers
app.include_router(author_router)
app.include_router(bookshelf_router)
app.include_router(category_router)
app.include_router(publisher_router)
app.include_router(brand_router)
app.include_router(series_router)

# Templates
templates = Jinja2Templates(directory="templates")

@app.get("/")
async def homepage(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})

@app.get("/books")
async def books_page(request: Request, page: int = 1, limit: int = 10, sort_by: str = "title", db: Session = Depends(get_db)):
    # Calculate offset
    offset = (page - 1) * limit

    # Get total count
    total_books = db.query(Book).count()
    total_pages = (total_books + limit - 1) // limit  # Ceiling division

    # Query with pagination and sorting
    if sort_by == "title":
        books = db.query(Book).order_by(Book.title).offset(offset).limit(limit).all()
    elif sort_by == "created_at":
        books = db.query(Book).order_by(Book.created_at.desc()).offset(offset).limit(limit).all()
    else:
        books = db.query(Book).offset(offset).limit(limit).all()

    return templates.TemplateResponse("books.html", {
        "request": request,
        "books": books,
        "page": page,
        "limit": limit,
        "total_pages": total_pages,
        "total_books": total_books,
        "sort_by": sort_by
    })

@app.get("/books/add")
async def add_book_page(request: Request, db: Session = Depends(get_db)):
    authors = db.query(Author).all()
    publishers = db.query(Publisher).all()
    brands = db.query(Brand).all()
    series = db.query(BookSeries).all()
    categories = db.query(Category).all()
    bookshelves = db.query(Bookshelf).all()
    return templates.TemplateResponse("books_add.html", {
        "request": request,
        "authors": authors,
        "publishers": publishers,
        "brands": brands,
        "series": series,
        "categories": categories,
        "bookshelves": bookshelves
    })

@app.post("/books/add")
async def add_book(request: Request, isbn: str = Form(...), title_cn: str = Form(""), title: str = Form(...), author_ids: List[int] = Form([]), translator: str = Form(None), publisher_id: int = Form(None), publish_date: str = Form(None), brand_id: int = Form(None), book_series_id: int = Form(None), binding_type: str = Form(""), paper_type: str = Form(""), pages: int = Form(None), book_count: int = Form(None), language: str = Form(""), compose_type: str = Form(""), price: float = Form(None), purchase_price: float = Form(None), purchase_date: str = Form(None), thumb_image: str = Form(""), link: str = Form(""), category_id: int = Form(None), bookshelf_id: int = Form(None), read_state: str = Form(""), catalog: str = Form(""), introduction: str = Form(""), summary: str = Form(""), registered: bool = Form(False), edition: str = Form(""), print: str = Form(""), printed_number: int = Form(None), douban_score: float = Form(None), purchase_store: str = Form(""), tags: str = Form(""), in_wish: bool = Form(False), db: Session = Depends(get_db)):
    # Parse tags
    tags_list = [x.strip() for x in tags.split(',') if x.strip()]

    # Get authors and translators
    authors = db.query(Author).filter(Author.id.in_(author_ids)).all()

    # Create book data
    book_data = {
        "isbn": isbn,
        "title_cn": title_cn,
        "title": title,
        "publisher_id": publisher_id,
        "publish_date": publish_date,
        "brand_id": brand_id,
        "book_series_id": book_series_id,
        "binding_type": binding_type,
        "paper_type": paper_type,
        "pages": pages,
        "book_count": book_count,
        "language": language,
        "compose_type": compose_type,
        "price": price,
        "purchase_price": purchase_price,
        "purchase_date": purchase_date,
        "thumb_image": thumb_image,
        "link": link,
        "category_id": category_id,
        "bookshelf_id": bookshelf_id,
        "read_state": read_state,
        "catalog": catalog,
        "introduction": introduction,
        "summary": summary,
        "registered": registered,
        "edition": edition,
        "print": print,
        "printed_number": printed_number,
        "douban_score": douban_score,
        "purchase_store": purchase_store,
        "tags": tags_list,
        "in_wish": in_wish,
        "created_at": datetime.utcnow(),
        "updated_at": datetime.utcnow(),
    }

    db_book = Book(**book_data)
    db_book.authors = authors
    db_book.translator = translator
    db.add(db_book)
    db.commit()
    db.refresh(db_book)

    return templates.TemplateResponse("books_add.html", {"request": request, "message": "Book added successfully!"})

app.include_router(book_router)

@app.get("/authors")
async def authors_page(request: Request, db: Session = Depends(get_db)):
    authors = db.query(Author).limit(10).all()
    return templates.TemplateResponse("authors.html", {"request": request, "authors": authors})

@app.get("/publishers")
async def publishers_page(request: Request, db: Session = Depends(get_db)):
    publishers = db.query(Publisher).limit(10).all()
    return templates.TemplateResponse("publishers.html", {"request": request, "publishers": publishers})

@app.get("/categories")
async def categories_page(request: Request, db: Session = Depends(get_db)):
    categories = db.query(Category).limit(10).all()
    return templates.TemplateResponse("categories.html", {"request": request, "categories": categories})

@app.get("/bookshelves")
async def bookshelves_page(request: Request, db: Session = Depends(get_db)):
    bookshelves = db.query(Bookshelf).limit(10).all()
    return templates.TemplateResponse("bookshelves.html", {"request": request, "bookshelves": bookshelves})

@app.get("/series")
async def series_page(request: Request, db: Session = Depends(get_db)):
    series = db.query(BookSeries).limit(10).all()
    return templates.TemplateResponse("series.html", {"request": request, "series": series})

@app.get("/brands")
async def brands_page(request: Request, db: Session = Depends(get_db)):
    brands = db.query(Brand).limit(10).all()
    return templates.TemplateResponse("brands.html", {"request": request, "brands": brands})

@app.get("/books/{book_id}")
async def book_details(request: Request, book_id: int, db: Session = Depends(get_db)):
    book = db.query(Book).filter(Book.id == book_id).first()
    if not book:
        raise HTTPException(status_code=404, detail="Book not found")
    return templates.TemplateResponse("book_details.html", {"request": request, "book": book})

@app.get("/authors/{author_id}")
async def author_details(request: Request, author_id: int, db: Session = Depends(get_db)):
    author = db.query(Author).filter(Author.id == author_id).first()
    if not author:
        raise HTTPException(status_code=404, detail="Author not found")
    return templates.TemplateResponse("author_details.html", {"request": request, "author": author})

@app.get("/publishers/{publisher_id}")
async def publisher_details(request: Request, publisher_id: int, db: Session = Depends(get_db)):
    publisher = db.query(Publisher).filter(Publisher.id == publisher_id).first()
    if not publisher:
        raise HTTPException(status_code=404, detail="Publisher not found")
    return templates.TemplateResponse("publisher_details.html", {"request": request, "publisher": publisher})

@app.get("/categories/{category_id}")
async def category_details(request: Request, category_id: int, db: Session = Depends(get_db)):
    category = db.query(Category).filter(Category.id == category_id).first()
    if not category:
        raise HTTPException(status_code=404, detail="Category not found")
    return templates.TemplateResponse("category_details.html", {"request": request, "category": category})

@app.get("/bookshelves/{bookshelf_id}")
async def bookshelf_details(request: Request, bookshelf_id: int, db: Session = Depends(get_db)):
    bookshelf = db.query(Bookshelf).filter(Bookshelf.id == bookshelf_id).first()
    if not bookshelf:
        raise HTTPException(status_code=404, detail="Bookshelf not found")
    return templates.TemplateResponse("bookshelf_details.html", {"request": request, "bookshelf": bookshelf})

@app.get("/series/{series_id}")
async def series_details(request: Request, series_id: int, db: Session = Depends(get_db)):
    series = db.query(BookSeries).filter(BookSeries.id == series_id).first()
    if not series:
        raise HTTPException(status_code=404, detail="Series not found")
    return templates.TemplateResponse("series_details.html", {"request": request, "series": series})

@app.get("/brands/{brand_id}")
async def brand_details(request: Request, brand_id: int, db: Session = Depends(get_db)):
    brand = db.query(Brand).filter(Brand.id == brand_id).first()
    if not brand:
        raise HTTPException(status_code=404, detail="Brand not found")
    return templates.TemplateResponse("brand_details.html", {"request": request, "brand": brand})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
