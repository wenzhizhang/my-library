"""Tests for book list sorting, including the `book_series` sort option.

Covers the main book list (`GET /api/books/`) and related-object book lists
(author, publisher) that expose `sort_by` for their detail pages.
"""

from models import Author, Book, BookSeries, Publisher


def _series(client, db, name):
    s = BookSeries(name=name)
    db.add(s)
    db.flush()
    return s


def _book(client, db, title, series=None, publisher=None, author=None):
    book = Book(title=title, book_series=series, publisher=publisher)
    if author is not None:
        book.authors = [author]
    db.add(book)
    return book


def test_book_list_sort_by_series(client, db):
    """GET /api/books/?sort_by=book_series groups by series name, no-series last."""
    alpha = _series(client, db, "Alpha Series")
    beta = _series(client, db, "Beta Series")
    _book(client, db, "No Series 1")
    _book(client, db, "Alpha 2", series=alpha)
    _book(client, db, "Alpha 1", series=alpha)
    _book(client, db, "Beta 1", series=beta)
    _book(client, db, "No Series 2")
    db.commit()

    resp = client.get("/api/books/", params={"page": 1, "limit": 20, "sort_by": "book_series"})
    assert resp.status_code == 200, resp.text
    titles = [b["title"] for b in resp.json()["books"]]

    # Alpha Series books (title order), then Beta Series, then books without a series
    assert titles == ["Alpha 1", "Alpha 2", "Beta 1", "No Series 1", "No Series 2"]


def test_author_books_sort_by_series(client, db):
    """Author detail book list supports sort_by=book_series."""
    author = Author(name="Test Author")
    db.add(author)
    db.flush()
    alpha = _series(client, db, "Alpha")
    beta = _series(client, db, "Beta")
    _book(client, db, "Zed", author=author)
    _book(client, db, "A2", series=alpha, author=author)
    _book(client, db, "A1", series=alpha, author=author)
    _book(client, db, "B1", series=beta, author=author)
    db.commit()

    resp = client.get(f"/api/authors/{author.id}/books", params={"sort_by": "book_series"})
    assert resp.status_code == 200, resp.text
    titles = [b["title"] for b in resp.json()["books"]]
    assert titles == ["A1", "A2", "B1", "Zed"]


def test_publisher_books_sort_by_series(client, db):
    """Publisher detail book list supports sort_by=book_series."""
    publisher = Publisher(name="Test Publisher")
    db.add(publisher)
    db.flush()
    alpha = _series(client, db, "Alpha")
    _book(client, db, "Zed", publisher=publisher)
    _book(client, db, "A2", series=alpha, publisher=publisher)
    _book(client, db, "A1", series=alpha, publisher=publisher)
    db.commit()

    resp = client.get(f"/api/publishers/{publisher.id}/books", params={"sort_by": "book_series"})
    assert resp.status_code == 200, resp.text
    titles = [b["title"] for b in resp.json()["books"]]
    assert titles == ["A1", "A2", "Zed"]


def test_book_list_default_sort_unchanged(client, db):
    """Default (title) sort still works when series are present."""
    alpha = _series(client, db, "Alpha Series")
    _book(client, db, "Zed", series=alpha)
    _book(client, db, "Alpha")
    db.commit()

    resp = client.get("/api/books/", params={"page": 1, "limit": 20})
    assert resp.status_code == 200, resp.text
    titles = [b["title"] for b in resp.json()["books"]]
    assert titles == ["Alpha", "Zed"]


def test_author_books_search_q(client, db):
    """Detail book lists support free-text q filtering (title/title_cn/isbn)."""
    author = Author(name="Test Author")
    db.add(author)
    db.flush()
    b1 = _book(client, db, "The Great Gatsby", author=author)
    _book(client, db, "Other Book", author=author)
    db.flush()
    b1.isbn = "978-7-5555-0000-1"
    db.commit()

    # By title
    resp = client.get(f"/api/authors/{author.id}/books", params={"q": "Gatsby"})
    assert resp.status_code == 200, resp.text
    titles = [b["title"] for b in resp.json()["books"]]
    assert titles == ["The Great Gatsby"]

    # By isbn
    resp = client.get(f"/api/authors/{author.id}/books", params={"q": "978-7-5555"})
    assert resp.status_code == 200, resp.text
    assert len(resp.json()["books"]) == 1

    # No match → empty list
    resp = client.get(f"/api/authors/{author.id}/books", params={"q": "zzz-nothing"})
    assert resp.status_code == 200, resp.text
    assert resp.json()["books"] == []
    assert resp.json()["total_books"] == 0
