"""Tests for reading plan CRUD endpoints (POST/PUT/DELETE/GET /api/reading-plans/)."""


def test_create_plan_minimal(client, db):
    """POST with minimal fields (name) → 200, defaults applied."""
    payload = {"name": "2026 Reading Plan"}
    resp = client.post("/api/reading-plans/", json=payload)
    assert resp.status_code == 200, resp.text
    data = resp.json()

    assert data["name"] == payload["name"]
    assert data["intro"] is None
    assert data["start_date"] is None
    assert data["end_date"] is None
    assert data["total_books"] == 0
    assert data["progress"] == 0.0
    assert data["books"] == []
    assert data["id"] is not None


def test_create_plan_with_dates(client, db):
    """POST with name and dates → dates stored correctly."""
    payload = {
        "name": "Summer Reading",
        "intro": "Books for summer 2026",
        "start_date": "2026-06-01",
        "end_date": "2026-08-31",
    }
    resp = client.post("/api/reading-plans/", json=payload)
    assert resp.status_code == 200, resp.text
    data = resp.json()

    assert data["name"] == payload["name"]
    assert data["intro"] == payload["intro"]
    assert data["start_date"] == "2026-06-01"
    assert data["end_date"] == "2026-08-31"


def test_list_plans(client, db):
    """GET / reading plans → paginated list."""
    # Create two plans
    client.post("/api/reading-plans/", json={"name": "Plan A"})
    client.post("/api/reading-plans/", json={"name": "Plan B"})

    resp = client.get("/api/reading-plans/")
    assert resp.status_code == 200
    data = resp.json()

    assert "reading_plans" in data
    assert data["total_plans"] == 2
    assert data["total_pages"] >= 1
    assert len(data["reading_plans"]) == 2


def test_get_plan_detail(client, db):
    """GET /{plan_id} → full detail with books."""
    from models import Author, Book

    # Create book with author
    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    book = Book(title="Test Book", isbn="978-7-01-000010-1")
    book.authors.append(author)
    db.add(book)
    db.commit()

    # Create plan
    resp = client.post("/api/reading-plans/", json={"name": "My Plan"})
    plan_id = resp.json()["id"]

    # Add book to plan
    client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": book.id})

    # Get detail
    resp = client.get(f"/api/reading-plans/{plan_id}")
    assert resp.status_code == 200
    data = resp.json()

    assert data["name"] == "My Plan"
    assert data["total_books"] == 1
    assert len(data["books"]) == 1
    assert data["books"][0]["title"] == "Test Book"


def test_update_plan(client, db):
    """PUT /{plan_id} → update name and dates."""
    resp = client.post("/api/reading-plans/", json={"name": "Original"})
    plan_id = resp.json()["id"]

    update = {
        "name": "Updated Plan",
        "intro": "Updated description",
        "start_date": "2026-01-01",
        "end_date": "2026-12-31",
    }
    resp = client.put(f"/api/reading-plans/{plan_id}", json=update)
    assert resp.status_code == 200
    data = resp.json()

    assert data["name"] == "Updated Plan"
    assert data["intro"] == "Updated description"
    assert data["start_date"] == "2026-01-01"
    assert data["end_date"] == "2026-12-31"


def test_delete_plan(client, db):
    """DELETE /{plan_id} → 200, plan removed."""
    resp = client.post("/api/reading-plans/", json={"name": "To Delete"})
    plan_id = resp.json()["id"]

    resp = client.delete(f"/api/reading-plans/{plan_id}")
    assert resp.status_code == 200
    assert resp.json() == {"message": "Reading plan deleted"}

    # Verify it's gone
    resp = client.get(f"/api/reading-plans/{plan_id}")
    assert resp.status_code == 404


def test_add_book_to_plan(client, db):
    """POST /{plan_id}/books → add single book."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    book = Book(title="Test Book", isbn="978-7-01-000020-1")
    book.authors.append(author)
    db.add(book)
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Plan"})
    plan_id = resp.json()["id"]

    resp = client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": book.id})
    assert resp.status_code == 200
    data = resp.json()

    assert data["total_books"] == 1
    assert len(data["books"]) == 1


def test_batch_add_books(client, db):
    """POST /{plan_id}/books/batch → add multiple books."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    books = []
    for i in range(3):
        book = Book(title=f"Book {i}", isbn=f"978-7-01-00003{i}-1")
        book.authors.append(author)
        db.add(book)
        books.append(book)
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Batch Plan"})
    plan_id = resp.json()["id"]

    resp = client.post(
        f"/api/reading-plans/{plan_id}/books/batch",
        json={"book_ids": [b.id for b in books]},
    )
    assert resp.status_code == 200
    data = resp.json()

    assert data["total_books"] == 3
    assert len(data["books"]) == 3


def test_remove_book_from_plan(client, db):
    """DELETE /{plan_id}/books/{book_id} → remove book from plan."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    book = Book(title="Test Book", isbn="978-7-01-000040-1")
    book.authors.append(author)
    db.add(book)
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Plan"})
    plan_id = resp.json()["id"]

    client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": book.id})

    resp = client.delete(f"/api/reading-plans/{plan_id}/books/{book.id}")
    assert resp.status_code == 200
    data = resp.json()

    assert data["total_books"] == 0
    assert len(data["books"]) == 0


def test_progress_with_mixed_read_states(client, db):
    """Progress = sum(read books' volumes) / sum(all volumes) × 100."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    # Book 1: read_state="read", book_count=1 → 1 volume read
    book1 = Book(title="Read Book", isbn="978-7-01-000050-1", read_state="read", book_count=1)
    book1.authors.append(author)

    # Book 2: read_state="reading", book_count=1 → 0 volumes read
    book2 = Book(title="Reading Book", isbn="978-7-01-000051-1", read_state="reading", book_count=1)
    book2.authors.append(author)

    # Book 3: read_state="unread", book_count=1 → 0 volumes read
    book3 = Book(title="Unread Book", isbn="978-7-01-000052-1", read_state="unread", book_count=1)
    book3.authors.append(author)

    db.add_all([book1, book2, book3])
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Progress Plan"})
    plan_id = resp.json()["id"]

    client.post(
        f"/api/reading-plans/{plan_id}/books/batch",
        json={"book_ids": [book1.id, book2.id, book3.id]},
    )

    resp = client.get(f"/api/reading-plans/{plan_id}")
    assert resp.status_code == 200
    data = resp.json()

    assert data["total_books"] == 3
    # 1 read volume / 3 total volumes ≈ 33.3%
    assert data["progress"] == 33.3


def test_progress_with_book_count(client, db):
    """Book with book_count=3 and read_state=read counts as 3 volumes."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    # Multi-volume book: 3 volumes, all read
    book1 = Book(title="Trilogy", isbn="978-7-01-000060-1", read_state="read", book_count=3)
    book1.authors.append(author)

    # Single book, unread
    book2 = Book(title="Single", isbn="978-7-01-000061-1", read_state="unread", book_count=1)
    book2.authors.append(author)

    db.add_all([book1, book2])
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Volume Plan"})
    plan_id = resp.json()["id"]

    client.post(
        f"/api/reading-plans/{plan_id}/books/batch",
        json={"book_ids": [book1.id, book2.id]},
    )

    resp = client.get(f"/api/reading-plans/{plan_id}")
    assert resp.status_code == 200
    data = resp.json()

    # Total volumes: 3 + 1 = 4; read volumes: 3 → 75.0%
    assert data["progress"] == 75.0


def test_progress_null_book_count_defaults_to_one(client, db):
    """NULL book_count defaults to 1 volume for progress calc."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    # book_count=NULL, read_state="read" → counts as 1 volume read
    book1 = Book(title="No Count", isbn="978-7-01-000070-1", read_state="read", book_count=None)
    book1.authors.append(author)

    # book_count=NULL, read_state="unread" → counts as 1 volume
    book2 = Book(title="Also No Count", isbn="978-7-01-000071-1", read_state="unread", book_count=None)
    book2.authors.append(author)

    db.add_all([book1, book2])
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Null Count Plan"})
    plan_id = resp.json()["id"]

    client.post(
        f"/api/reading-plans/{plan_id}/books/batch",
        json={"book_ids": [book1.id, book2.id]},
    )

    resp = client.get(f"/api/reading-plans/{plan_id}")
    assert resp.status_code == 200
    data = resp.json()

    # 1 read volume / 2 total volumes = 50.0%
    assert data["progress"] == 50.0


def test_plan_not_found(client, db):
    """GET non-existent plan → 404."""
    resp = client.get("/api/reading-plans/99999")
    assert resp.status_code == 404
    assert resp.json()["detail"] == "Reading plan not found"


def test_add_nonexistent_book(client, db):
    """POST book that doesn't exist → 404."""
    resp = client.post("/api/reading-plans/", json={"name": "Plan"})
    plan_id = resp.json()["id"]

    resp = client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": 99999})
    assert resp.status_code == 404
    assert "not found" in resp.json()["detail"].lower()


def test_duplicate_book_in_plan(client, db):
    """Adding same book twice → 400."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    book = Book(title="Test Book", isbn="978-7-01-000080-1")
    book.authors.append(author)
    db.add(book)
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "Plan"})
    plan_id = resp.json()["id"]

    # Add once — OK
    resp = client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": book.id})
    assert resp.status_code == 200

    # Add again — conflict
    resp = client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": book.id})
    assert resp.status_code == 400
    assert "already in plan" in resp.json()["detail"].lower()


def test_search_plans(client, db):
    """GET / with q param filters by name."""
    client.post("/api/reading-plans/", json={"name": "Summer Reading"})
    client.post("/api/reading-plans/", json={"name": "Winter Reading"})
    client.post("/api/reading-plans/", json={"name": "Fitness Plan"})

    resp = client.get("/api/reading-plans/?q=Reading")
    assert resp.status_code == 200
    data = resp.json()

    assert data["total_plans"] == 2
    names = [p["name"] for p in data["reading_plans"]]
    assert "Summer Reading" in names
    assert "Winter Reading" in names
    assert "Fitness Plan" not in names


def test_list_plan_summary_has_progress(client, db):
    """List response includes progress for each plan."""
    from models import Author, Book

    author = Author(name="Test Author")
    db.add(author)
    db.commit()

    book = Book(title="Read Book", isbn="978-7-01-000090-1", read_state="read", book_count=1)
    book.authors.append(author)
    db.add(book)
    db.commit()

    resp = client.post("/api/reading-plans/", json={"name": "With Progress"})
    plan_id = resp.json()["id"]

    client.post(f"/api/reading-plans/{plan_id}/books", json={"book_id": book.id})

    resp = client.get("/api/reading-plans/")
    assert resp.status_code == 200
    data = resp.json()

    plan = data["reading_plans"][0]
    assert plan["progress"] == 100.0
    assert plan["total_books"] == 1
