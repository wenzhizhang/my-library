"""Tests for stored weight columns: recompute hooks, weight sort, exposure.

weight = count of non-wishlist, non-archived books linked to each object.
Recompute runs synchronously after every book/collection mutation.
"""

from models import Author, Publisher, Brand, Category, BookSeries, BookCollection, Book


def _book_payload(isbn, title, author_ids=None, publisher_id=None, brand_id=None,
                  series_id=None, category_id=None, in_wish=False):
    return {
        "isbn": isbn,
        "title_cn": title,
        "title": title,
        "author_ids": author_ids or [],
        "publisher_id": publisher_id,
        "brand_id": brand_id,
        "book_series_id": series_id,
        "category_id": category_id,
        "in_wish": in_wish,
    }


def test_create_book_recomputes_and_exposes_weights(client, db):
    author = Author(name="A", name_cn="甲")
    publisher = Publisher(name="P")
    brand = Brand(name="B")
    series = BookSeries(name="S")
    category = Category(name="C")
    db.add_all([author, publisher, brand, series, category])
    db.commit()

    resp = client.post("/api/books/", json=_book_payload(
        "978-1", "T1", author_ids=[author.id], publisher_id=publisher.id,
        brand_id=brand.id, series_id=series.id, category_id=category.id))
    assert resp.status_code == 200, resp.text

    # Detail responses expose weight
    assert client.get(f"/api/authors/{author.id}").json()["weight"] == 1
    assert client.get(f"/api/publishers/{publisher.id}").json()["weight"] == 1
    assert client.get(f"/api/brands/{brand.id}").json()["weight"] == 1
    assert client.get(f"/api/series/{series.id}").json()["weight"] == 1
    assert client.get(f"/api/categories/{category.id}").json()["weight"] == 1

    # List responses expose weight too
    authors = client.get("/api/authors/").json()["authors"]
    assert authors[0]["weight"] == 1


def test_wishlist_and_archived_books_do_not_count(client, db):
    author = Author(name="A", name_cn="甲")
    db.add(author)
    db.commit()

    client.post("/api/books/", json=_book_payload("978-2", "T2", author_ids=[author.id]))
    client.post("/api/books/", json=_book_payload("978-3", "T3", author_ids=[author.id], in_wish=True))
    assert client.get(f"/api/authors/{author.id}").json()["weight"] == 1

    book = db.query(Book).filter(Book.isbn == "978-2").first()
    resp = client.put(f"/api/books/{book.id}/archive")
    assert resp.status_code == 200, resp.text
    assert client.get(f"/api/authors/{author.id}").json()["weight"] == 0


def test_collection_add_remove_recomputes_weight(client, db):
    collection = BookCollection(name="Col")
    db.add(collection)
    db.commit()

    client.post("/api/books/", json=_book_payload("978-4", "T4"))
    book = db.query(Book).filter(Book.isbn == "978-4").first()

    resp = client.post(f"/api/book-collections/{collection.id}/books", json={"book_id": book.id})
    assert resp.status_code == 200, resp.text
    assert client.get(f"/api/book-collections/{collection.id}").json()["weight"] == 1

    client.delete(f"/api/book-collections/{collection.id}/books/{book.id}")
    assert client.get(f"/api/book-collections/{collection.id}").json()["weight"] == 0


def test_authors_default_sort_by_weight(client, db):
    heavy = Author(name="Heavy", name_cn="重")
    light_b = Author(name="B", name_cn="乙")
    light_a = Author(name="A", name_cn="甲")
    db.add_all([heavy, light_a, light_b])
    db.commit()
    client.post("/api/books/", json=_book_payload("978-5", "T5", author_ids=[heavy.id]))
    client.post("/api/books/", json=_book_payload("978-6", "T6", author_ids=[heavy.id]))
    client.post("/api/books/", json=_book_payload("978-7", "T7", author_ids=[light_a.id]))

    resp = client.get("/api/authors/", params={"limit": 10})
    assert resp.status_code == 200, resp.text
    names = [a["name"] for a in resp.json()["authors"]]
    # weight desc (Heavy=2 first), then name asc for the weight-1/0 tie... actually:
    # Heavy(2) > light_a(1) > light_b(0)
    assert names == ["Heavy", "A", "B"]


def test_publishers_weight_sort_param(client, db):
    p_heavy = Publisher(name="P1")
    p_light = Publisher(name="P2")
    db.add_all([p_heavy, p_light])
    db.commit()
    client.post("/api/books/", json=_book_payload("978-8", "T8", publisher_id=p_heavy.id))
    client.post("/api/books/", json=_book_payload("978-9", "T9", publisher_id=p_heavy.id))

    resp = client.get("/api/publishers/", params={"sort_by": "weight"})
    assert resp.status_code == 200, resp.text
    names = [p["name"] for p in resp.json()["publishers"]]
    assert names == ["P1", "P2"]
    assert resp.json()["publishers"][0]["weight"] == 2
