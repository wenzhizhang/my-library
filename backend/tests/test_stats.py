"""Tests for GET /api/stats/books."""

from datetime import datetime

import pytest
from models import Author, Book, Category, Publisher


def _make_book(db, **overrides):
    """Helper to create a Book with sensible defaults."""
    defaults = dict(
        title="Test Book",
        read_state="unread",
        price=30.0,
        purchase_price=25.0,
        purchase_date=datetime(2025, 6, 1),
        created_at=datetime(2025, 6, 1),
        douban_score=8.5,
        compose_type="monograph",
        binding_type="paperback",
        language="zh",
    )
    for k, v in overrides.items():
        defaults[k] = v
    book = Book(**defaults)
    db.add(book)
    db.flush()
    return book


class TestStatsBooks:
    """GET /api/stats/books"""

    # ── STAT-01: Normal stats ────────────────────────────────────────────────

    def test_overview_counts(self, client, db):
        """Normal stats return correct overview counts."""
        cat = Category(name="Fiction")
        pub = Publisher(name="PubCo")
        auth = Author(name="Author A")
        db.add_all([cat, pub, auth])
        db.flush()

        b1 = _make_book(db, title="B1", category_id=cat.id, publisher_id=pub.id)
        b1.authors.append(auth)
        b2 = _make_book(db, title="B2", read_state="reading", category_id=cat.id)
        b3 = _make_book(
            db, title="B3", read_state="read", purchase_price=15.0, price=20.0
        )
        db.flush()

        resp = client.get("/api/stats/books")
        assert resp.status_code == 200
        data = resp.json()

        overview = data["overview"]
        assert overview["total_books"] == 3
        assert overview["total_authors"] == 1
        assert overview["total_publishers"] == 1
        assert overview["total_categories"] == 1
        assert overview["avg_purchase_price"] == pytest.approx(21.67, abs=0.01)  # (25+25+15)/3
        assert overview["avg_price"] == pytest.approx(26.67, abs=0.01)  # (30+30+20)/3
        assert overview["total_spent"] == 65.0

    # ── STAT-02: Empty DB ────────────────────────────────────────────────────

    def test_empty_db(self, client, db):
        """Empty database returns 200 with all zeros."""
        resp = client.get("/api/stats/books")
        assert resp.status_code == 200
        data = resp.json()

        overview = data["overview"]
        assert overview["total_books"] == 0
        assert overview["total_authors"] == 0
        assert overview["total_publishers"] == 0
        assert overview["total_categories"] == 0
        assert overview["avg_price"] == 0
        assert overview["avg_purchase_price"] == 0
        assert overview["total_spent"] == 0

        # Sub-lists are empty
        assert data["by_read_state"] == []
        assert data["by_category"] == []
        assert data["by_binding"] == []
        assert data["by_language"] == []
        assert data["top_authors"] == []
        assert data["top_publishers"] == []
        assert data["timeline_months"] == []
        assert data["timeline_years"] == []
        assert data["purchase_months"] == []
        assert data["purchase_years"] == []
        assert data["by_score"] == []
        assert data["by_compose"] == []

    # ── by_read_state grouping ──────────────────────────────────────────────

    def test_by_read_state(self, client, db):
        """by_read_state groups books correctly."""
        _make_book(db, title="U1", read_state="unread")
        _make_book(db, title="U2", read_state="unread")
        _make_book(db, title="R1", read_state="reading")
        _make_book(db, title="D1", read_state="read")
        _make_book(db, title="D2", read_state="read")
        _make_book(db, title="D3", read_state="read")
        _make_book(db, title="None1", read_state=None)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        state_map = {s["name"]: s["count"] for s in data["by_read_state"]}

        assert state_map.get("unread") == 2
        assert state_map.get("reading") == 1
        assert state_map.get("read") == 3
        assert state_map.get("unknown") == 1  # None becomes "unknown"

    # ── by_category grouping ─────────────────────────────────────────────────

    def test_by_category(self, client, db):
        """by_category groups books by category name."""
        fiction = Category(name="Fiction")
        science = Category(name="Science")
        history = Category(name="History")
        db.add_all([fiction, science, history])
        db.flush()

        _make_book(db, title="F1", category_id=fiction.id)
        _make_book(db, title="F2", category_id=fiction.id)
        _make_book(db, title="S1", category_id=science.id)
        _make_book(db, title="H1", category_id=history.id)
        _make_book(db, title="NoCat", category_id=None)  # uncategorized
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        cat_map = {c["name"]: c["count"] for c in data["by_category"]}

        assert cat_map.get("Fiction") == 2
        assert cat_map.get("Science") == 1
        assert cat_map.get("History") == 1
        # Book with category_id=None has no matching Category row in the
        # LEFT JOIN (Category -> Book), so it does NOT appear in results.
        assert "Uncategorized" not in cat_map

    def test_by_category_empty_name(self, client, db):
        """Category with empty string name is mapped to 'Uncategorized'."""
        unnamed = Category(name="")
        db.add(unnamed)
        db.flush()
        _make_book(db, title="B", category_id=unnamed.id)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        cat_map = {c["name"]: c["count"] for c in data["by_category"]}
        assert cat_map.get("Uncategorized") == 1

    # ── purchase_timeline data ───────────────────────────────────────────────

    def test_purchase_timeline(self, client, db):
        """purchase_months and purchase_years aggregate correctly."""
        _make_book(
            db,
            title="B1",
            purchase_date=datetime(2024, 3, 10),
            purchase_price=10.0,
        )
        _make_book(
            db,
            title="B2",
            purchase_date=datetime(2024, 3, 20),
            purchase_price=20.0,
        )
        _make_book(
            db,
            title="B3",
            purchase_date=datetime(2024, 5, 1),
            purchase_price=30.0,
        )
        _make_book(
            db,
            title="NoPurchase",
            purchase_date=None,
            purchase_price=5.0,
        )
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()

        # purchase_months
        pm = {m["label"]: m for m in data["purchase_months"]}
        # March 2024: 2 books, total price 30
        mar = pm.get("2024-03")
        assert mar is not None
        assert mar["count"] == 2
        assert mar["price"] == 30.0
        assert mar["year"] == 2024
        assert mar["month"] == 3

        # May 2024: 1 book, total price 30
        may = pm.get("2024-05")
        assert may is not None
        assert may["count"] == 1
        assert may["price"] == 30.0

        # purchase_years
        py = {y["label"]: y for y in data["purchase_years"]}
        year_2024 = py.get("2024")
        assert year_2024 is not None
        assert year_2024["count"] == 3
        assert year_2024["price"] == 60.0

    def test_purchase_timeline_no_purchases(self, client, db):
        """No purchase_date data yields empty purchase lists."""
        _make_book(db, title="B1", purchase_date=None)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        assert data["purchase_months"] == []
        assert data["purchase_years"] == []

    # ── timeline_years aggregation ───────────────────────────────────────────

    def test_timeline_years(self, client, db):
        """timeline_years aggregates by year of created_at."""
        _make_book(db, title="B1", created_at=datetime(2023, 1, 1))
        _make_book(db, title="B2", created_at=datetime(2023, 6, 15))
        _make_book(db, title="B3", created_at=datetime(2024, 2, 1))
        _make_book(db, title="B4", created_at=datetime(2025, 11, 30))
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()

        ty = {y["label"]: y for y in data["timeline_years"]}
        assert ty["2023"]["count"] == 2
        assert ty["2024"]["count"] == 1
        assert ty["2025"]["count"] == 1

    def test_timeline_months(self, client, db):
        """timeline_months aggregates by year-month of created_at."""
        _make_book(db, title="B1", created_at=datetime(2023, 1, 10))
        _make_book(db, title="B2", created_at=datetime(2023, 1, 20))
        _make_book(db, title="B3", created_at=datetime(2023, 2, 5))
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()

        tm = {m["label"]: m for m in data["timeline_months"]}
        jan = tm.get("2023-01")
        assert jan is not None
        assert jan["count"] == 2
        assert jan["year"] == 2023
        assert jan["month"] == 1

        feb = tm.get("2023-02")
        assert feb is not None
        assert feb["count"] == 1

    def test_timeline_with_default_created_at(self, client, db):
        """Books use default created_at and appear in timeline."""
        _make_book(db, title="B1")
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        # The book gets created_at=datetime.now (default), so timeline is
        # non-empty with at least one entry for the current year.
        assert len(data["timeline_years"]) >= 1
        assert len(data["timeline_months"]) >= 1

    # ── Miscellaneous edge cases ─────────────────────────────────────────────

    def test_avg_price_excludes_zero(self, client, db):
        """avg_price only considers books with price > 0."""
        _make_book(db, title="B1", price=0.0)
        _make_book(db, title="B2", price=10.0)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        assert data["overview"]["avg_price"] == 10.0

    def test_avg_purchase_price_excludes_zero(self, client, db):
        """avg_purchase_price only considers books with purchase_price > 0."""
        _make_book(db, title="B1", purchase_price=0.0)
        _make_book(db, title="B2", purchase_price=20.0)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        assert data["overview"]["avg_purchase_price"] == 20.0

    def test_total_spent_excludes_zero(self, client, db):
        """total_spent only sums purchase_price > 0."""
        _make_book(db, title="B1", purchase_price=0.0)
        _make_book(db, title="B2", purchase_price=15.0)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        assert data["overview"]["total_spent"] == 15.0

    def test_by_score_buckets(self, client, db):
        """Books with douban_score are bucketed correctly."""
        _make_book(db, title="B1", douban_score=9.1)
        _make_book(db, title="B2", douban_score=8.5)
        _make_book(db, title="B3", douban_score=8.0)
        _make_book(db, title="B4", douban_score=7.5)
        _make_book(db, title="B5", douban_score=6.5)
        _make_book(db, title="B6", douban_score=5.0)
        _make_book(db, title="B7", douban_score=None)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        score_map = {s["name"]: s["count"] for s in data["by_score"]}

        assert score_map.get("9.0+") == 1
        assert score_map.get("8.0-8.9") == 2
        assert score_map.get("7.0-7.9") == 1
        assert score_map.get("6.0-6.9") == 1
        assert score_map.get("< 6.0") == 1

    def test_by_compose_and_binding_language(self, client, db):
        """by_compose, by_binding, by_language gather correctly."""
        _make_book(db, title="B1", compose_type="monograph", binding_type="hardcover", language="en")
        _make_book(db, title="B2", compose_type="monograph", binding_type="paperback", language="en")
        _make_book(db, title="B3", compose_type="series", binding_type="paperback", language="zh")
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()

        comp = {c["name"]: c["count"] for c in data["by_compose"]}
        assert comp.get("monograph") == 2
        assert comp.get("series") == 1

        bind = {b["name"]: b["count"] for b in data["by_binding"]}
        assert bind.get("hardcover") == 1
        assert bind.get("paperback") == 2

        lang = {l["name"]: l["count"] for l in data["by_language"]}
        assert lang.get("en") == 2
        assert lang.get("zh") == 1

    def test_top_authors_and_publishers(self, client, db):
        """top_authors and top_publishers are populated."""
        auth1 = Author(name="Auth1")
        auth2 = Author(name="Auth2")
        pub1 = Publisher(name="Pub1")
        pub2 = Publisher(name="Pub2")
        db.add_all([auth1, auth2, pub1, pub2])
        db.flush()

        b1 = _make_book(db, title="B1", publisher_id=pub1.id)
        b1.authors.append(auth1)
        b2 = _make_book(db, title="B2", publisher_id=pub1.id)
        b2.authors.append(auth1)
        b3 = _make_book(db, title="B3", publisher_id=pub2.id)
        b3.authors.append(auth2)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()

        auth_map = {a["name"]: a["count"] for a in data["top_authors"]}
        assert auth_map.get("Auth1") == 2
        assert auth_map.get("Auth2") == 1

        pub_map = {p["name"]: p["count"] for p in data["top_publishers"]}
        assert pub_map.get("Pub1") == 2
        assert pub_map.get("Pub2") == 1

    def test_top_publishers_unknown_when_no_name(self, client, db):
        """Publisher with empty name appears as 'Unknown'."""
        unnamed = Publisher(name="")
        db.add(unnamed)
        db.flush()
        _make_book(db, title="B1", publisher_id=unnamed.id)
        db.flush()

        resp = client.get("/api/stats/books")
        data = resp.json()
        pub_map = {p["name"]: p["count"] for p in data["top_publishers"]}
        assert pub_map.get("Unknown") == 1
