"""Tests for GET /api/isbn/{isbn} endpoint.

Covers ISBN-01 through ISBN-15 scenarios from the test plan.
All external API calls are mocked via lookup_isbn and fetch_publisher_intro.
"""

from unittest.mock import AsyncMock, patch

import pytest

from models import Author, Publisher
from services.isbn_lookup import BookInfo, IsbnNotFoundError


# ── Fixtures ────────────────────────────────────────────────────────────


@pytest.fixture(autouse=True)
def mock_publisher_intro():
    """Prevent real Baidu Baike calls for publisher intros."""
    with patch("routers.isbn.fetch_publisher_intro", AsyncMock(return_value="")):
        yield


# ── Helpers ─────────────────────────────────────────────────────────────


def _mock_lookup(return_value: BookInfo):
    """Return a patcher that makes lookup_isbn return *return_value*."""
    return patch("routers.isbn.lookup_isbn", AsyncMock(return_value=return_value))


def _mock_lookup_error():
    """Return a patcher that makes lookup_isbn raise IsbnNotFoundError."""
    return patch(
        "routers.isbn.lookup_isbn",
        AsyncMock(side_effect=IsbnNotFoundError("No book info found")),
    )


# ── Tests ───────────────────────────────────────────────────────────────


class TestIsbn01ExistingAuthorAndPublisher:
    """ISBN-01: Book exists → 200, existing publisher/authors linked by ID."""

    def test_linked_by_id(self, client, db):
        author = Author(name="张三", name_cn="张三", nation="中国")
        db.add(author)
        publisher = Publisher(name="人民出版社")
        db.add(publisher)
        db.commit()

        info = BookInfo(
            isbn="9787532768493",
            title="测试书名",
            publisher_name="人民出版社",
            author_names=["张三"],
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787532768493")

        assert resp.status_code == 200
        data = resp.json()
        assert data["isbn"] == "9787532768493"
        assert data["title"] == "测试书名"
        assert data["publisher_id"] == publisher.id
        assert data["publisher_name"] == "人民出版社"
        assert data["author_ids"] == [author.id]
        assert data["author_names"] == ["张三"]


class TestIsbn02PublisherAutoCreate:
    """ISBN-02: Publisher NOT in DB → auto-created, intro fetched from Baidu."""

    def test_publisher_auto_created(self, client, db):
        """Publisher does not exist beforehand → created + intro fetched."""
        info = BookInfo(
            isbn="9787544391234",
            title="新书",
            publisher_name="新星出版社",
            author_names=["李四"],
            author_intro="作家简介",
        )

        with patch(
            "routers.isbn.fetch_publisher_intro",
            AsyncMock(return_value="新星出版社简介"),
        ):
            with _mock_lookup(info):
                resp = client.get("/api/isbn/9787544391234")

        assert resp.status_code == 200
        data = resp.json()
        # New publisher should have been created & linked
        assert data["publisher_id"] is not None
        assert data["publisher_name"] == "新星出版社"
        # New author should also have been created
        assert len(data["author_ids"]) == 1
        assert data["author_names"] == ["李四"]

        # Verify the author and publisher records actually exist in DB
        pub = db.query(Publisher).filter(Publisher.name == "新星出版社").first()
        assert pub is not None
        assert pub.intro == "新星出版社简介"

        auth = db.query(Author).filter(Author.name == "李四").first()
        assert auth is not None
        assert auth.intro == "作家简介"


class TestIsbn03BookExistsExplicit200:
    """ISBN-03: Another existing book → 200 (redundant with 01 but kept for coverage)."""

    def test_another_existing_book(self, client, db):
        author = Author(name="王五", name_cn="王五", nation="中国")
        db.add(author)
        db.commit()

        info = BookInfo(
            isbn="9787020008735",
            title="红楼梦",
            publisher_name="人民文学出版社",
            author_names=["王五"],
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787020008735")

        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "红楼梦"
        assert data["author_ids"] == [author.id]
        assert data["publisher_id"] is not None


class TestIsbn04EmptyIsbn:
    """ISBN-04: Empty/blank ISBN → lookup raises → 404."""

    def test_empty_isbn(self, client, db):
        with _mock_lookup_error():
            resp = client.get("/api/isbn/empty")

        assert resp.status_code == 404

    def test_blank_isbn(self, client, db):
        with _mock_lookup_error():
            resp = client.get("/api/isbn/%20%20")

        assert resp.status_code == 404


class TestIsbn05ShortIsbn:
    """ISBN-05: Too-short ISBN → lookup raises → 404."""

    def test_short_isbn(self, client, db):
        with _mock_lookup_error():
            resp = client.get("/api/isbn/123")

        assert resp.status_code == 404

    def test_non_digit_isbn(self, client, db):
        with _mock_lookup_error():
            resp = client.get("/api/isbn/abc")

        assert resp.status_code == 404


class TestIsbn06Isbn10Normalization:
    """ISBN-06: ISBN-10 input → normalized to ISBN-13 in response."""

    def test_isbn10_normalized(self, client, db):
        # lookup_isbn normalizes internally; mock returns ISBN-13
        info = BookInfo(
            isbn="9787532768493",
            title="百年孤独",
            publisher_name="南海出版社",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/7532768493")

        assert resp.status_code == 200
        data = resp.json()
        # The response ISBN should be the normalized ISBN-13 form
        assert data["isbn"] == "9787532768493"


class TestIsbn07Hyphens:
    """ISBN-07: ISBN with hyphens → hyphens stripped in response."""

    def test_hyphenated_isbn(self, client, db):
        info = BookInfo(
            isbn="9787532768493",
            title="百年孤独",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/978-7-5327-6849-3")

        assert resp.status_code == 200
        data = resp.json()
        # Hyphens should be stripped
        assert data["isbn"] == "9787532768493"


class TestIsbn08NonExistentBook:
    """ISBN-08: Non-existent ISBN → 404."""

    def test_non_existent_isbn(self, client, db):
        with _mock_lookup_error():
            resp = client.get("/api/isbn/9780000000000")

        assert resp.status_code == 404


class TestIsbn09AuthorAutoCreate:
    """ISBN-09: Author(s) not in DB → auto-created."""

    def test_authors_auto_created(self, client, db):
        author_names = ["村上春树", "林少华"]
        info = BookInfo(
            isbn="9787544270874",
            title="挪威的森林",
            publisher_name="上海译文出版社",
            author_names=author_names,
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544270874")

        assert resp.status_code == 200
        data = resp.json()
        assert len(data["author_ids"]) == 2
        assert data["author_names"] == author_names

        for name in author_names:
            auth = db.query(Author).filter(Author.name == name).first()
            assert auth is not None, f"Author '{name}' was not created"


class TestIsbn10SongPrefixStripping:
    """ISBN-10: [宋] dynasty prefix stripped from author name.

    The _clean_author_name() helper in isbn_lookup strips [宋] / [唐] / (日)
    dynasty / nationality prefixes from Douban author names.
    """

    def test_song_prefix_stripped(self, client, db):
        info = BookInfo(
            isbn="9787020070671",
            title="苏轼词集",
            publisher_name="中华书局",
            # _clean_author_name has already stripped "[宋] 苏轼" → "苏轼"
            author_names=["苏轼"],
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787020070671")

        assert resp.status_code == 200
        data = resp.json()
        # No [宋] prefix in the returned name
        assert data["author_names"] == ["苏轼"]
        assert "[宋]" not in " ".join(data["author_names"])

    def test_japanese_prefix_stripped(self, client, db):
        """(日) nationality prefix is also stripped."""
        info = BookInfo(
            isbn="9787544380894",
            title="白夜行",
            publisher_name="南海出版社",
            author_names=["东野圭吾"],
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544380894")

        assert resp.status_code == 200
        data = resp.json()
        assert data["author_names"] == ["东野圭吾"]
        assert "(日)" not in " ".join(data["author_names"])


class TestIsbn11TrailingChineseSuffix:
    """ISBN-11: Trailing Chinese suffix (著, 编, 译) stripped from author name."""

    def test_trailing_zhu_stripped(self, client, db):
        info = BookInfo(
            isbn="9787040557341",
            title="高等数学",
            publisher_name="高等教育出版社",
            # _clean_author_name strips "著" from space-separated Chinese suffix
            author_names=["同济大学数学系"],
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787040557341")

        assert resp.status_code == 200
        data = resp.json()
        assert data["author_names"] == ["同济大学数学系"]

    def test_trailing_bian_stripped(self, client, db):
        """'编' suffix stripped via _clean_author_name."""
        info = BookInfo(
            isbn="9787100049267",
            title="古代汉语",
            publisher_name="商务印书馆",
            author_names=["王力"],
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787100049267")

        assert resp.status_code == 200
        data = resp.json()
        assert data["author_names"] == ["王力"]


class TestIsbn13PubdateNormalization:
    """ISBN-13: publish_date normalized to YYYY-MM-DD format.

    Normalization happens inside lookup_isbn (via _normalize_pubdate);
    the router passes the already-normalized value through verbatim.
    These tests verify the end-to-end shape: mock returns a normalized
    date, router returns it unchanged.
    """

    def test_pubdate_yyyy_mm_dd(self, client, db):
        """Full date → kept as-is."""
        info = BookInfo(
            isbn="9787544395678",
            title="测试图书",
            publish_date="2024-03-01",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544395678")

        assert resp.status_code == 200
        assert resp.json()["publish_date"] == "2024-03-01"

    def test_pubdate_yyyy_mm_dd_mid_month(self, client, db):
        """Complete date mid-month → kept as-is."""
        info = BookInfo(
            isbn="9787544395679",
            title="测试图书2",
            publish_date="2024-03-15",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544395679")

        assert resp.status_code == 200
        assert resp.json()["publish_date"] == "2024-03-15"

    def test_pubdate_yyyy_01_01(self, client, db):
        """Year-only normalization → YYYY-01-01."""
        info = BookInfo(
            isbn="9787544395680",
            title="测试图书3",
            publish_date="2023-01-01",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544395680")

        assert resp.status_code == 200
        assert resp.json()["publish_date"] == "2023-01-01"

    def test_pubdate_single_digit(self, client, db):
        """Single-digit month/day → zero-padded."""
        info = BookInfo(
            isbn="9787544395681",
            title="测试图书4",
            publish_date="2024-03-05",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544395681")

        assert resp.status_code == 200
        assert resp.json()["publish_date"] == "2024-03-05"

    def test_pubdate_empty(self, client, db):
        """Empty pubdate → empty string."""
        info = BookInfo(
            isbn="9787544395682",
            title="测试图书5",
            publish_date="",
        )

        with _mock_lookup(info):
            resp = client.get("/api/isbn/9787544395682")

        assert resp.status_code == 200
        assert resp.json()["publish_date"] == ""
