"""Tests for book CRUD endpoints (POST/PUT/DELETE/GET /api/books/).

NOTE: The POST/PUT response goes through `response_model=BookResponse`,
which maps flat ID fields (e.g. publisher_id) to nested objects (e.g. publisher).
Since the handler returns flat IDs, the output object fields are always null.
"""


def test_book_01_create_minimal(client, db):
    """POST with minimal fields (isbn, title_cn, title) → 200, defaults applied."""
    from models import Author, Publisher, Category

    # Setup FK references
    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    payload = {
        "isbn": "978-7-01-000001-1",
        "title_cn": "测试书籍",
        "title": "Test Book",
        "publisher_id": publisher.id,
        "category_id": category.id,
        "author_ids": [],
    }
    resp = client.post("/api/books/", json=payload)
    assert resp.status_code == 200, resp.text
    data = resp.json()

    # Core fields match
    assert data["isbn"] == payload["isbn"]
    assert data["title_cn"] == payload["title_cn"]
    assert data["title"] == payload["title"]

    # Defaults from BookCreation schema should be applied
    assert data["binding_type"] == "精装"
    assert data["paper_type"] == "胶版纸"
    assert data["pages"] == 1
    assert data["book_count"] == 1
    assert data["language"] == "中文"
    assert data["compose_type"] == "横排"
    assert data["read_state"] == "unread"
    assert data["registered"] is False
    assert data["thumb_image"] == ""
    assert data["catalog"] == ""
    assert data["introduction"] == ""
    assert data["summary"] == ""
    assert data["purchase_store"] == ""
    assert data["edition"] == ""
    assert data["printing_info"] == ""
    assert data["tags"] == []
    assert data["in_wish"] is None

    # Relationship fields become null because handler returns flat IDs,
    # but BookResponse schema expects nested objects
    assert data["publisher"] is None
    assert data["category"] is None
    assert data["brand"] is None
    assert data["book_series"] is None
    assert data["bookshelf"] is None

    # Authors is null (no author_ids provided -> empty -> no authors)
    assert data["authors"] is None


def test_book_03_create_without_author_ids(client, db):
    """POST without author_ids → 200, book has no authors."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    payload = {
        "isbn": "978-7-01-000002-8",
        "title_cn": "无作者书籍",
        "title": "Book Without Authors",
        "author_ids": [],
    }
    resp = client.post("/api/books/", json=payload)
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["authors"] is None


def test_book_04_update_partial(client, db):
    """PUT with partial fields → 200, unchanged fields retain original values."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    # Create a book first
    create_payload = {
        "isbn": "978-7-01-000003-5",
        "title_cn": "原中文名",
        "title": "Original Title",
        "publisher_id": publisher.id,
        "pages": 100,
        "author_ids": [],
    }
    resp = client.post("/api/books/", json=create_payload)
    assert resp.status_code == 200
    book_id = resp.json()["id"]

    # Partial update: only change title and pages
    update_payload = {
        "title": "Updated Title",
        "pages": 200,
    }
    resp = client.put(f"/api/books/{book_id}", json=update_payload)
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["title"] == "Updated Title"
    assert data["pages"] == 200
    # Fields not sent retain original values
    assert data["title_cn"] == "原中文名"
    assert data["isbn"] == "978-7-01-000003-5"


def test_book_05_delete(client, db):
    """DELETE → 200, book gone."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    create_payload = {
        "isbn": "978-7-01-000004-2",
        "title_cn": "待删除书籍",
        "title": "Book To Delete",
        "author_ids": [],
    }
    resp = client.post("/api/books/", json=create_payload)
    assert resp.status_code == 200
    book_id = resp.json()["id"]

    # Delete
    resp = client.delete(f"/api/books/{book_id}")
    assert resp.status_code == 200
    assert resp.json() == {"message": "Book deleted"}

    # Verify gone
    resp = client.get(f"/api/books/{book_id}")
    assert resp.status_code == 404


def test_book_07_list_paginated(client, db):
    """GET list → 200, paginated response."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    # Create a few books
    for i in range(3):
        payload = {
            "isbn": f"978-7-01-00000{i + 5}-8",
            "title_cn": f"书籍{i + 1}",
            "title": f"Book{i + 1}",
            "author_ids": [],
        }
        resp = client.post("/api/books/", json=payload)
        assert resp.status_code == 200

    # List with page=1, limit=2
    resp = client.get("/api/books/?page=1&limit=2")
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert "books" in data
    assert "total_pages" in data
    assert "total_books" in data
    assert len(data["books"]) == 2
    assert data["total_books"] == 3
    assert data["total_pages"] == 2

    # Second page should have 1 book
    resp = client.get("/api/books/?page=2&limit=2")
    assert resp.status_code == 200
    data2 = resp.json()
    assert len(data2["books"]) == 1


def test_book_08_search_q(client, db):
    """GET with ?q= → searches title + title_cn + isbn."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    # Create books with distinct searchable fields
    client.post("/api/books/", json={
        "isbn": "111-1-11-111111-1",
        "title_cn": "中文标题A",
        "title": "Alpha Book",
        "author_ids": [],
    })
    client.post("/api/books/", json={
        "isbn": "222-2-22-222222-2",
        "title_cn": "中文标题B",
        "title": "Beta Book",
        "author_ids": [],
    })

    # Search by title
    resp = client.get("/api/books/?q=Alpha")
    assert resp.status_code == 200
    titles = [b["title"] for b in resp.json()["books"]]
    assert "Alpha Book" in titles
    assert "Beta Book" not in titles

    # Search by title_cn
    resp = client.get("/api/books/?q=中文标题B")
    assert resp.status_code == 200
    titles = [b["title"] for b in resp.json()["books"]]
    assert "Beta Book" in titles

    # Search by isbn
    resp = client.get("/api/books/?q=111-1-11")
    assert resp.status_code == 200
    titles = [b["title"] for b in resp.json()["books"]]
    assert "Alpha Book" in titles


def test_book_09_filter_purchase_year(client, db):
    """GET with ?purchase_year= → filter by year."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    # Create books with different purchase years
    client.post("/api/books/", json={
        "isbn": "333-3-33-333333-3",
        "title_cn": "2023年购书",
        "title": "Book 2023",
        "purchase_date": "2023-06-15T00:00:00",
        "author_ids": [],
    })
    client.post("/api/books/", json={
        "isbn": "444-4-44-444444-4",
        "title_cn": "2024年购书",
        "title": "Book 2024",
        "purchase_date": "2024-03-10T00:00:00",
        "author_ids": [],
    })

    # Filter by year 2023
    resp = client.get("/api/books/?purchase_year=2023")
    assert resp.status_code == 200
    titles = [b["title"] for b in resp.json()["books"]]
    assert "Book 2023" in titles
    assert "Book 2024" not in titles


def test_book_10_filter_purchase_month(client, db):
    """GET with ?purchase_month= → filter by month."""
    from models import Author, Publisher, Category

    author = Author(name="Test Author", name_cn="测试作者")
    publisher = Publisher(name="Test Publisher")
    category = Category(name="Test Category")
    db.add_all([author, publisher, category])
    db.commit()

    # Create books with different purchase months
    client.post("/api/books/", json={
        "isbn": "555-5-55-555555-5",
        "title_cn": "六月购书",
        "title": "June Book",
        "purchase_date": "2024-06-01T00:00:00",
        "author_ids": [],
    })
    client.post("/api/books/", json={
        "isbn": "666-6-66-666666-6",
        "title_cn": "七月购书",
        "title": "July Book",
        "purchase_date": "2024-07-15T00:00:00",
        "author_ids": [],
    })

    # Filter by month 6 (June)
    resp = client.get("/api/books/?purchase_month=6")
    assert resp.status_code == 200
    titles = [b["title"] for b in resp.json()["books"]]
    assert "June Book" in titles
    assert "July Book" not in titles


def test_book_11_detail_lists_authors_as_strings(client, db):
    """Detail /books endpoints → authors are display strings, not objects.

    The BookCard renders authors via `join(', ')`, which only works with
    string entries; every book-list endpoint must match the /api/books/
    contract (str(Author) → "[dynasty] name_cn").
    """
    from models import (
        Author, Book, Category, Publisher, Brand, BookSeries,
        Bookshelf, BookCollection, ReadingPlan,
    )

    author = Author(name="Rowling", name_cn="罗琳", dynasty="当代")
    category = Category(name="Sci-fi")
    publisher = Publisher(name="ACM Press")
    brand = Brand(name="Brand One")
    series = BookSeries(name="Series One")
    bookshelf = Bookshelf(name="Shelf One")
    collection = BookCollection(name="Collection One")
    plan = ReadingPlan(name="Plan One")
    db.add_all([author, category, publisher, brand, series, bookshelf, collection, plan])
    db.commit()

    book = Book(title="Test Book", isbn="978-7-01-000100-1")
    book.authors.append(author)
    book.category = category
    book.publisher = publisher
    book.brand = brand
    book.book_series = series
    book.bookshelf = bookshelf
    book.collections.append(collection)
    book.reading_plans.append(plan)
    db.add(book)

    # Wishlist/archived books exercise the /api/books/* list endpoints,
    # which filter by in_wish/archived respectively.
    wishlist_book = Book(title="Wish Book", isbn="978-7-01-000101-8", in_wish=True)
    wishlist_book.authors.append(author)
    db.add(wishlist_book)
    archived_book = Book(title="Archived Book", isbn="978-7-01-000102-5", archived=True)
    db.add(archived_book)
    db.commit()

    # url → expected authors (the archived book has no author)
    endpoints = {
        f"/api/authors/{author.id}/books": ["[当代] 罗琳"],
        f"/api/categories/{category.id}/books": ["[当代] 罗琳"],
        f"/api/publishers/{publisher.id}/books": ["[当代] 罗琳"],
        f"/api/brands/{brand.id}/books": ["[当代] 罗琳"],
        f"/api/series/{series.id}/books": ["[当代] 罗琳"],
        f"/api/bookshelves/{bookshelf.id}/books": ["[当代] 罗琳"],
        f"/api/book-collections/{collection.id}/books": ["[当代] 罗琳"],
        f"/api/reading-plans/{plan.id}/books": ["[当代] 罗琳"],
        "/api/books/": ["[当代] 罗琳"],
        "/api/books/wishlist": ["[当代] 罗琳"],
        "/api/books/archived": [],
    }
    for url, expected_authors in endpoints.items():
        resp = client.get(url)
        assert resp.status_code == 200, (url, resp.text)
        books = resp.json()["books"]
        assert len(books) == 1, url
        assert books[0]["authors"] == expected_authors, (url, books[0]["authors"])
