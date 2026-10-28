"""Metadata adapters, merging and caching."""

import asyncio
from pathlib import Path

from services.metadata import cache as metadata_cache
from services.metadata import covers, dangdang, douban, service
from services.metadata.fields import SourceResult, merge_results

FIXTURES = Path(__file__).parent / "fixtures" / "metadata"


def test_douban_parse_extracts_fields():
    html = (FIXTURES / "douban.html").read_text(encoding="utf-8")

    result = douban.parse(html, isbn="9787020002207", link="https://book.douban.com/subject/1/")

    assert result is not None
    assert result.source == "douban"
    assert result.fields["title_cn"] == "红楼梦（下）"
    assert result.fields["author_names"] == ["曹雪芹"]
    assert result.fields["publisher_name"] == "人民文学出版社"
    assert result.fields["publish_date"] == "1982"
    assert result.fields["pages"] == 771
    assert result.fields["price"] == 59.7
    assert result.fields["binding_type"] == "平装"
    assert result.fields["douban_score"] == 9.6
    assert result.fields["thumb_image"] == "https://img.example/douban-cover.jpg"
    assert result.fields["summary"].startswith("《红楼梦》")
    assert result.fields["tags"] == ["古典文学", "小说"]


def test_dangdang_parse_uses_list_price_and_skips_placeholders():
    content = (FIXTURES / "dangdang.html").read_bytes()

    results = dangdang.parse_search(content, isbn="9787020002207")

    assert len(results) == 2
    first, second = results
    assert first.fields["title_cn"].startswith("红楼梦")
    assert first.fields["author_names"] == ["曹雪芹"]
    assert first.fields["publisher_name"] == "人民文学出版社"
    assert first.fields["publish_date"] == "1982-03-01"
    assert first.fields["price"] == 59.7
    assert first.fields["thumb_image"] == "https://img.example/dangdang-cover.jpg"
    assert "thumb_image" not in second.fields


def test_merge_uses_field_authority_and_reports_conflicts():
    merged = merge_results(
        [
            SourceResult(
                "douban",
                {
                    "title_cn": "红楼梦",
                    "publisher_name": "人民文学出版社",
                    "thumb_image": "https://img.example/d.jpg",
                },
            ),
            SourceResult(
                "dangdang",
                {
                    "title_cn": "红楼梦（上下册）",
                    "publisher_name": "人民文学",
                    "price": 59.7,
                    "publish_date": "1982-03-01",
                    "thumb_image": "https://img.example/dd.jpg",
                },
            ),
        ]
    )

    assert merged.fields["publisher_name"] == "人民文学出版社"
    assert merged.fields["price"] == 59.7
    assert merged.sources["price"] == "dangdang"
    assert merged.sources["publisher_name"] == "douban"
    assert merged.conflicts["publisher_name"]["dangdang"] == "人民文学"
    # Same year and a different cover file are not conflicts.
    assert "publish_date" not in merged.conflicts
    assert "thumb_image" not in merged.conflicts
    assert "translator" in merged.missing


def test_cache_roundtrip_and_expiry(tmp_path, monkeypatch):
    monkeypatch.setenv("METADATA_CACHE_PATH", str(tmp_path / "cache.db"))
    metadata_cache.set("isbn:1", "douban", {"fields": {"title_cn": "A"}}, ttl=60)

    assert metadata_cache.get("isbn:1")["fields"]["title_cn"] == "A"
    assert metadata_cache.get("isbn:1", now=10**12) is None


def test_cover_download_sends_referer_and_stores_locally(tmp_path, monkeypatch):
    captured = {}

    class FakeResponse:
        status_code = 200
        headers = {"content-type": "image/jpeg"}
        content = b"fake-jpeg"

    class FakeClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def get(self, url, headers=None):
            captured["url"] = url
            captured["headers"] = headers or {}
            return FakeResponse()

    monkeypatch.setenv("MEDIA_LOCAL_DIR", str(tmp_path / "media"))
    monkeypatch.setattr(covers.httpx, "AsyncClient", lambda **kwargs: FakeClient())

    path = asyncio.run(
        covers.download("https://img.example/d.jpg", referer="https://book.douban.com/")
    )

    assert path.startswith("/images/books/") and path.endswith(".jpg")
    assert (tmp_path / "media" / path.lstrip("/")).is_file()
    assert captured["headers"]["Referer"] == "https://book.douban.com/"


def test_lookup_isbn_metadata_caches(tmp_path, monkeypatch):
    monkeypatch.setenv("METADATA_CACHE_PATH", str(tmp_path / "cache.db"))
    monkeypatch.setenv("MEDIA_LOCAL_DIR", str(tmp_path / "media"))
    calls = []

    async def fake_douban(isbn):
        calls.append(isbn)
        return SourceResult(
            "douban",
            {
                "isbn": isbn,
                "title_cn": "红楼梦",
                "author_names": ["曹雪芹"],
                "publisher_name": "人民文学出版社",
                "publish_date": "1982",
                "price": 59.7,
            },
        )

    monkeypatch.setattr(service.douban, "fetch", fake_douban)

    first = asyncio.run(service.lookup_isbn_metadata("9787020002207"))
    second = asyncio.run(service.lookup_isbn_metadata("9787020002207"))

    assert first.fields["title_cn"] == "红楼梦"
    assert second.fields["title_cn"] == "红楼梦"
    assert calls == ["9787020002207"]


def test_search_metadata_scores_title_and_author(monkeypatch):
    async def fake_search(title, author):
        return [
            SourceResult("dangdang", {"title_cn": "红楼梦", "author_names": ["曹雪芹"]}),
            SourceResult("dangdang", {"title_cn": "西游记", "author_names": ["吴承恩"]}),
        ]

    monkeypatch.setattr(service.dangdang, "search", fake_search)

    results = asyncio.run(service.search_metadata("红楼梦", "曹雪芹"))

    assert results
    assert results[0]["fields"]["title_cn"] == "红楼梦"
    assert results[0]["score"] > 0


def test_isbn_lookup_endpoint_returns_merged_fields(client, monkeypatch):
    from routers import isbn as isbn_router
    from services.metadata.fields import MergedMetadata

    async def fake_lookup(raw):
        return MergedMetadata(
            fields={"isbn": raw, "title_cn": "红楼梦"},
            sources={"title_cn": "douban"},
            missing=["price"],
            source="douban",
        )

    monkeypatch.setattr(isbn_router, "lookup_isbn_metadata", fake_lookup)

    response = client.post("/api/isbn/lookup", json={"isbn": "9787020002207"})

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["fields"]["title_cn"] == "红楼梦"
    assert body["sources"]["title_cn"] == "douban"
    assert "price" in body["missing"]


def test_isbn_lookup_endpoint_falls_back_to_title_search(client, monkeypatch):
    from routers import isbn as isbn_router

    async def fake_search(title, author, publisher, publish_date):
        return [
            {
                "fields": {"isbn": "9787020002207", "title_cn": "红楼梦"},
                "sources": {"title_cn": "dangdang"},
                "score": 0.9,
            }
        ]

    monkeypatch.setattr(isbn_router, "search_metadata", fake_search)

    response = client.post(
        "/api/isbn/lookup", json={"title": "红楼梦", "author": "曹雪芹"}
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["fields"]["title_cn"] == "红楼梦"
    assert body["candidates"][0]["score"] == 0.9


def test_to_book_info_falls_back_to_summary_for_introduction():
    from services.metadata.fields import MergedMetadata

    merged = MergedMetadata(
        fields={"isbn": "978", "title_cn": "书", "summary": "内容简介"},
        source="douban",
    )
    info = service.to_book_info(merged)
    assert info.introduction == "内容简介"


def test_isbn_lookup_does_not_adopt_a_weak_candidate(client, monkeypatch):
    from routers import isbn as isbn_router

    async def weak_search(title, author, publisher, publish_date):
        return [
            {
                "fields": {"title_cn": "可能相关的书"},
                "sources": {"title_cn": "dangdang"},
                "score": 0.55,
            },
            {
                "fields": {"title_cn": "另一本"},
                "sources": {"title_cn": "dangdang"},
                "score": 0.53,
            },
        ]

    monkeypatch.setattr(isbn_router, "search_metadata", weak_search)

    response = client.post("/api/isbn/lookup", json={"title": "红楼梦"})

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["fields"] == {}
    assert len(body["candidates"]) == 2


def test_rate_limit_cooldown():
    from services.metadata import rate_limit

    rate_limit.reset()
    assert not rate_limit.in_cooldown("douban")
    rate_limit.set_cooldown("douban", seconds=60)
    assert rate_limit.in_cooldown("douban")
    rate_limit.reset()
    assert not rate_limit.in_cooldown("douban")


def test_throttle_waits_between_calls(monkeypatch):
    import time

    from services.metadata import rate_limit

    monkeypatch.setenv("METADATA_TEST_INTERVAL", "0.05")
    rate_limit.reset()

    async def run():
        start = time.monotonic()
        await rate_limit.throttle("test")
        await rate_limit.throttle("test")
        return time.monotonic() - start

    assert asyncio.run(run()) >= 0.04
