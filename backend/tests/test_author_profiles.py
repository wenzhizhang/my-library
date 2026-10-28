"""Author filters: dynasty normalization and nation fallback."""

from models import Author, Book
from services.author_profiles import authors_by_dynasty, normalize_dynasty


def _author(db, name, dynasty, nation="中国"):
    author = Author(name=name, name_cn=name, dynasty=dynasty, nation=nation)
    db.add(author)
    db.commit()
    book = Book(title=f"{name}的书", title_cn=f"{name}的书")
    book.authors.append(author)
    db.add(book)
    db.commit()
    return author


def test_normalize_dynasty():
    assert normalize_dynasty("宋朝") == "宋"
    assert normalize_dynasty("宋代") == "宋"
    assert normalize_dynasty("宋") == "宋"


def test_authors_by_dynasty_excludes_southern_song_and_filters_nation(db):
    _author(db, "苏轼", "宋")
    _author(db, "王安石", "北宋")
    _author(db, "谢灵运", "南朝宋")
    _author(db, "刘义庆", "刘宋")
    _author(db, "洋作者", "宋", nation="美国")

    names = {row["name"] for row in authors_by_dynasty(db, "宋朝")}
    assert {"苏轼", "王安石"} <= names
    assert "谢灵运" not in names
    assert "刘义庆" not in names

    chinese = {row["name"] for row in authors_by_dynasty(db, "宋", "中国")}
    assert "洋作者" not in chinese


def test_authors_by_rating(db):
    from services.author_profiles import authors_by_rating

    a = _author(db, "高分公司", "宋")
    b = _author(db, "低分公司", "宋")
    book_a = Book(title="高分书", title_cn="高分书", douban_score=9.5)
    book_a.authors.append(a)
    book_b = Book(title="低分书", title_cn="低分书", douban_score=7.0)
    book_b.authors.append(b)
    db.add_all([book_a, book_b])
    db.commit()

    rows = authors_by_rating(db)
    assert rows[0]["name"] == "高分公司"
    assert rows[0]["score"] == 9.5
