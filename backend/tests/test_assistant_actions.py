"""Draft/commit flow for assistant book actions."""

from datetime import datetime

from models import Author, Book, Publisher
from services import assistant_actions
from services.metadata.fields import MergedMetadata

ISBN = "9787020002207"


def _merged(isbn: str = ISBN) -> MergedMetadata:
    return MergedMetadata(
        fields={
            "isbn": isbn,
            "title_cn": "红楼梦",
            "title": "红楼梦",
            "author_names": ["曹雪芹"],
            "publisher_name": "人民文学出版社",
            "publish_date": "1982-03-01",
            "pages": 771,
            "price": 59.7,
            "thumb_image": "/images/books/test.jpg",
        },
        sources={
            "title_cn": "douban",
            "author_names": "douban",
            "publisher_name": "douban",
            "price": "dangdang",
        },
        missing=["translator"],
        source="douban",
    )


def _patch_metadata(monkeypatch):
    async def fake_lookup(raw):
        return _merged()

    monkeypatch.setattr(assistant_actions, "lookup_isbn_metadata", fake_lookup)


def test_create_draft_does_not_write(client, db, monkeypatch):
    _patch_metadata(monkeypatch)

    response = client.post("/api/assistant/actions", json={"message": f"录入 {ISBN}"})

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["intent"] == "create_book"
    draft = body["draft"]
    assert draft["fields"]["title_cn"] == "红楼梦"
    assert draft["sources"]["price"] == "dangdang"
    assert draft["existing"] is None
    assert db.query(Book).filter(Book.isbn == ISBN).count() == 0


def test_revise_draft_by_chat(client, db, monkeypatch):
    _patch_metadata(monkeypatch)
    draft = client.post(
        "/api/assistant/actions", json={"message": f"录入 {ISBN}"}
    ).json()["draft"]

    response = client.post(
        "/api/assistant/actions",
        json={"draft": draft, "message": "出版社改成中华书局"},
    )

    assert response.status_code == 200, response.text
    revised = response.json()["draft"]
    assert revised["fields"]["publisher_name"] == "中华书局"
    assert revised["sources"]["publisher_name"] == "user"


def test_duplicate_isbn_offers_update_abandon_or_add(client, db, monkeypatch):
    _patch_metadata(monkeypatch)
    existing = Book(isbn=ISBN, title_cn="已有的书", title="已有的书")
    db.add(existing)
    db.commit()

    draft = client.post(
        "/api/assistant/actions", json={"message": f"录入 {ISBN}"}
    ).json()["draft"]
    assert draft["existing"]["book_id"] == existing.id

    response = client.post("/api/assistant/commit", json={"draft": draft})
    assert response.status_code == 409, response.text
    assert response.json()["detail"]["existing"]["book_id"] == existing.id

    abandoned = client.post(
        "/api/assistant/commit", json={"draft": draft, "duplicate_choice": "abandon"}
    )
    assert abandoned.status_code == 200, abandoned.text
    assert abandoned.json()["status"] == "abandoned"
    assert db.query(Book).filter(Book.isbn == ISBN).count() == 1

    added = client.post(
        "/api/assistant/commit", json={"draft": draft, "duplicate_choice": "add"}
    )
    assert added.status_code == 200, added.text
    assert added.json()["status"] == "created"
    assert db.query(Book).filter(Book.isbn == ISBN).count() == 2

    updated = client.post(
        "/api/assistant/commit", json={"draft": draft, "duplicate_choice": "update"}
    )
    assert updated.status_code == 200, updated.text
    assert updated.json()["status"] == "updated"
    db.refresh(existing)
    assert existing.title_cn == "红楼梦"


def test_commit_creates_book_with_authors_and_publisher(client, db, monkeypatch):
    _patch_metadata(monkeypatch)
    draft = client.post(
        "/api/assistant/actions", json={"message": f"录入 {ISBN}"}
    ).json()["draft"]

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    assert response.json()["status"] == "created"
    book = db.query(Book).filter(Book.isbn == ISBN).one()
    assert book.title_cn == "红楼梦"
    assert [author.name_cn for author in book.authors] == ["曹雪芹"]
    assert book.publisher.name == "人民文学出版社"
    assert db.query(Author).filter(Author.name_cn == "曹雪芹").count() == 1
    assert db.query(Publisher).filter(Publisher.name == "人民文学出版社").count() == 1


def test_update_only_writes_changed_fields_and_checks_updated_at(client, db):
    book = Book(isbn="9787020000999", title_cn="旧书名", title="旧书名")
    db.add(book)
    db.commit()
    db.refresh(book)
    draft = assistant_actions.build_update_draft(book, "出版社改成中华书局")
    assert draft["fields"] == {"publisher_name": "中华书局"}

    stale = dict(draft)
    stale["updated_at"] = "2000-01-01T00:00:00"
    response = client.post("/api/assistant/commit", json={"draft": stale})
    assert response.status_code == 409, response.text

    response = client.post("/api/assistant/commit", json={"draft": draft})
    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.title_cn == "旧书名"
    assert book.publisher.name == "中华书局"


def test_actions_require_login(guest):
    response = guest.post(
        "/api/assistant/actions", json={"message": f"录入 {ISBN}"}
    )
    assert response.status_code == 401


def test_lookup_failure_returns_manual_draft(client, db, monkeypatch):
    async def empty_lookup(raw):
        return MergedMetadata(fields={"isbn": raw}, sources={}, missing=["title_cn"])

    monkeypatch.setattr(assistant_actions, "lookup_isbn_metadata", empty_lookup)

    response = client.post(
        "/api/assistant/actions", json={"message": "录入 9787020002207"}
    )

    assert response.status_code == 200, response.text
    draft = response.json()["draft"]
    assert draft["lookup_failed"] is True
    assert draft["fields"] == {"isbn": "9787020002207"}

    commit = client.post("/api/assistant/commit", json={"draft": draft})
    assert commit.status_code == 422


def test_focused_field_change_creates_update_draft(client, db):
    book = Book(isbn="9787020000998", title_cn="三体", title="三体")
    db.add(book)
    db.commit()

    response = client.post(
        "/api/assistant/actions",
        json={"message": "出版社改成中华书局", "focus_book_id": book.id},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["intent"] == "update_book"
    assert body["draft"]["fields"] == {"publisher_name": "中华书局"}
    assert body["draft"]["book_id"] == book.id


def test_join_library_phrase_creates_a_draft(client, db, monkeypatch):
    _patch_metadata(monkeypatch)

    response = client.post(
        "/api/assistant/actions",
        json={"message": f"把这本书加入书库：{ISBN}"},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["intent"] == "create_book"
    assert body["draft"]["fields"]["title_cn"] == "红楼梦"


def test_update_multiple_fields_across_the_full_schema(client, db):
    book = Book(isbn="9787020002000", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    db.refresh(book)

    draft = assistant_actions.build_update_draft(
        book,
        "购入价格改成50，阅读状态改成已读，印数改为1000，装帧形式改成精装",
    )
    assert draft["fields"] == {
        "purchase_price": 50.0,
        "read_state": "read",
        "printed_number": 1000,
        "compose_type": "精装",
    }
    assert {row["field"] for row in draft["diff"]} == set(draft["fields"])

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.purchase_price == 50.0
    assert book.read_state == "read"
    assert book.printed_number == 1000
    assert book.compose_type == "精装"
    assert book.title_cn == "旧书"


def test_update_without_changes_is_rejected(client, db):
    book = Book(isbn="9787020002001", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    draft = assistant_actions.build_update_draft(book, "修改这本书")

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 422


def test_fill_missing_only_fills_empty_fields(client, db, monkeypatch):
    book = Book(isbn="9787020002002", title_cn="红楼梦", title="红楼梦")
    db.add(book)
    db.commit()
    db.refresh(book)

    async def fake_lookup(isbn):
        return MergedMetadata(
            fields={
                "isbn": isbn,
                "title_cn": "红楼梦（别的版本）",
                "publisher_name": "人民文学出版社",
                "price": 59.7,
                "thumb_image": "/images/books/fill.jpg",
            },
            sources={"publisher_name": "douban", "price": "dangdang"},
            source="douban",
        )

    monkeypatch.setattr(assistant_actions, "lookup_isbn_metadata", fake_lookup)

    response = client.post(
        "/api/assistant/actions",
        json={"action": "fill_missing", "focus_book_id": book.id},
    )

    assert response.status_code == 200, response.text
    draft = response.json()["draft"]
    # The existing title must not be overwritten by another edition's title.
    assert "title_cn" not in draft["fields"]
    assert draft["fields"]["publisher_name"] == "人民文学出版社"
    assert draft["fields"]["price"] == 59.7

    commit = client.post("/api/assistant/commit", json={"draft": draft})
    assert commit.status_code == 200, commit.text
    db.refresh(book)
    assert book.title_cn == "红楼梦"
    assert book.publisher.name == "人民文学出版社"
    assert book.price == 59.7


def test_edit_by_isbn_finds_the_book(client, db):
    book = Book(isbn="9787020002003", title_cn="三体", title="三体")
    db.add(book)
    db.commit()

    response = client.post(
        "/api/assistant/actions",
        json={"message": "修改 9787020002003 的出版社改成中华书局"},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["intent"] == "update_book"
    assert body["draft"]["book_id"] == book.id
    assert body["draft"]["fields"]["publisher_name"] == "中华书局"


def test_update_can_change_the_isbn(client, db):
    book = Book(isbn="9787020003000", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    db.refresh(book)
    draft = assistant_actions.build_update_draft(book, "ISBN改成9787020003001")

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.isbn == "9787020003001"


def test_update_book_without_isbn_does_not_require_one(client, db):
    book = Book(isbn=None, title_cn="无 ISBN 的书", title="无 ISBN 的书")
    db.add(book)
    db.commit()
    draft = assistant_actions.build_update_draft(book, "出版社改成中华书局")

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.publisher.name == "中华书局"
    assert book.isbn is None


def test_refresh_draft_rebuilds_with_latest_updated_at(client, db):
    book = Book(isbn="9787020003002", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    db.refresh(book)
    draft = assistant_actions.build_update_draft(book, "出版社改成中华书局")

    response = client.post(
        "/api/assistant/actions",
        json={
            "action": "refresh_draft",
            "draft": draft,
            "focus_book_id": book.id,
        },
    )

    assert response.status_code == 200, response.text
    fresh = response.json()["draft"]
    assert fresh["current"]["title_cn"] == "旧书"
    assert fresh["fields"]["publisher_name"] == "中华书局"
    assert fresh["updated_at"] == book.updated_at.isoformat()


def test_join_library_phrase_creates_a_draft(client, db, monkeypatch):
    _patch_metadata(monkeypatch)

    response = client.post(
        "/api/assistant/actions",
        json={"message": f"把这本书加入书库：{ISBN}"},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["intent"] == "create_book"
    assert body["draft"]["fields"]["title_cn"] == "红楼梦"


def test_update_multiple_fields_across_the_full_schema(client, db):
    book = Book(isbn="9787020002000", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    db.refresh(book)

    draft = assistant_actions.build_update_draft(
        book,
        "购入价格改成50，阅读状态改成已读，印数改为1000，装帧形式改成精装",
    )
    assert draft["fields"] == {
        "purchase_price": 50.0,
        "read_state": "read",
        "printed_number": 1000,
        "compose_type": "精装",
    }
    assert {row["field"] for row in draft["diff"]} == set(draft["fields"])

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.purchase_price == 50.0
    assert book.read_state == "read"
    assert book.printed_number == 1000
    assert book.compose_type == "精装"
    assert book.title_cn == "旧书"


def test_update_without_changes_is_rejected(client, db):
    book = Book(isbn="9787020002001", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    draft = assistant_actions.build_update_draft(book, "修改这本书")

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 422


def test_fill_missing_only_fills_empty_fields(client, db, monkeypatch):
    book = Book(isbn="9787020002002", title_cn="红楼梦", title="红楼梦")
    db.add(book)
    db.commit()
    db.refresh(book)

    async def fake_lookup(isbn):
        return MergedMetadata(
            fields={
                "isbn": isbn,
                "title_cn": "红楼梦（别的版本）",
                "publisher_name": "人民文学出版社",
                "price": 59.7,
                "thumb_image": "/images/books/fill.jpg",
            },
            sources={"publisher_name": "douban", "price": "dangdang"},
            source="douban",
        )

    monkeypatch.setattr(assistant_actions, "lookup_isbn_metadata", fake_lookup)

    response = client.post(
        "/api/assistant/actions",
        json={"action": "fill_missing", "focus_book_id": book.id},
    )

    assert response.status_code == 200, response.text
    draft = response.json()["draft"]
    # The existing title must not be overwritten by another edition's title.
    assert "title_cn" not in draft["fields"]
    assert draft["fields"]["publisher_name"] == "人民文学出版社"
    assert draft["fields"]["price"] == 59.7

    commit = client.post("/api/assistant/commit", json={"draft": draft})
    assert commit.status_code == 200, commit.text
    db.refresh(book)
    assert book.title_cn == "红楼梦"
    assert book.publisher.name == "人民文学出版社"
    assert book.price == 59.7


def test_edit_by_isbn_finds_the_book(client, db):
    book = Book(isbn="9787020002003", title_cn="三体", title="三体")
    db.add(book)
    db.commit()

    response = client.post(
        "/api/assistant/actions",
        json={"message": "修改 9787020002003 的出版社改成中华书局"},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["intent"] == "update_book"
    assert body["draft"]["book_id"] == book.id
    assert body["draft"]["fields"]["publisher_name"] == "中华书局"


def test_update_can_change_the_isbn(client, db):
    book = Book(isbn="9787020003000", title_cn="旧书", title="旧书")
    db.add(book)
    db.commit()
    db.refresh(book)
    draft = assistant_actions.build_update_draft(book, "ISBN改成9787020003001")

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.isbn == "9787020003001"


def test_update_book_without_isbn_does_not_require_one(client, db):
    book = Book(isbn=None, title_cn="无 ISBN 的书", title="无 ISBN 的书")
    db.add(book)
    db.commit()
    draft = assistant_actions.build_update_draft(book, "出版社改成中华书局")

    response = client.post("/api/assistant/commit", json={"draft": draft})

    assert response.status_code == 200, response.text
    db.refresh(book)
    assert book.publisher.name == "中华书局"
    assert book.isbn is None
