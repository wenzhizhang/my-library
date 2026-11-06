"""POST /api/books/{id}/clone duplicates a book completely."""

from datetime import datetime

from models import Author, Book, BookCollection, Category, Publisher, ReadingPlan


def test_clone_copies_fields_and_authors(client, db):
    author = Author(name="Author", name_cn="作者", nation="中国", dynasty="当代")
    publisher = Publisher(name="出版社")
    category = Category(name="分类")
    collection = BookCollection(name="书单")
    plan = ReadingPlan(name="阅读计划")
    db.add_all([author, publisher, category, collection, plan])
    db.commit()

    source = Book(
        isbn="978-7-01-000099-9",
        title_cn="被复制的书",
        title="Source Book",
        translator="译者",
        publisher_id=publisher.id,
        publish_date=datetime(2020, 1, 1),
        binding_type="精装",
        paper_type="胶版纸",
        pages=321,
        book_count=2,
        language="中文",
        compose_type="横排",
        price=99.0,
        purchase_price=66.0,
        purchase_date=datetime(2021, 2, 3),
        thumb_image="/images/books/cover.webp",
        link="https://example.com/book",
        category_id=category.id,
        read_state="read",
        catalog="目录",
        introduction="简介",
        summary="摘要",
        registered=True,
        edition="第1版",
        printing_info="第2次印刷",
        printed_number=3000,
        douban_score=8.8,
        purchase_store="书店",
        tags=["古典", "精装"],
        in_wish=False,
        archived=False,
    )
    source.authors = [author]
    source.collections = [collection]
    source.reading_plans = [plan]
    db.add(source)
    db.commit()

    response = client.post(f"/api/books/{source.id}/clone")

    assert response.status_code == 200, response.text
    clone_id = response.json()["id"]
    assert clone_id != source.id
    clone = db.query(Book).filter(Book.id == clone_id).one()

    from routers.book import CLONE_EXCLUDED_FIELDS

    for column in Book.__table__.columns:
        if column.name in CLONE_EXCLUDED_FIELDS:
            continue
        assert getattr(clone, column.name) == getattr(source, column.name)
    assert [author.id for author in clone.authors] == [author.id]
    assert [item.id for item in clone.collections] == [collection.id]
    assert [item.id for item in clone.reading_plans] == [plan.id]
    assert clone.tags == ["古典", "精装"]
    assert clone.tags is not source.tags


def test_clone_preserves_archive_and_wish_flags(client, db):
    source = Book(title_cn="归档书", title="Archived", archived=True, in_wish=True)
    db.add(source)
    db.commit()

    response = client.post(f"/api/books/{source.id}/clone")

    assert response.status_code == 200, response.text
    clone = db.query(Book).filter(Book.id == response.json()["id"]).one()
    assert clone.archived is True
    assert clone.in_wish is True
