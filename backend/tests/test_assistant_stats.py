"""Assistant statistics questions over the existing stats aggregate."""

from models import Author, Book, Category, Publisher
from services import assistant_stats


def _answer(db, message):
    query = assistant_stats.parse_stats_query(message)
    return assistant_stats.answer_stats_query(db, query, message)


def _seed(db):
    author = Author(name="Liu", name_cn="刘慈欣")
    publisher = Publisher(name="人民文学出版社")
    category = Category(name="科幻")
    db.add_all([author, publisher, category])
    db.commit()
    for index, (title, state) in enumerate(
        [("三体", "read"), ("流浪地球", "reading"), ("球状闪电", "unread")]
    ):
        book = Book(
            title=title,
            title_cn=title,
            read_state=state,
            publisher_id=publisher.id,
            category_id=category.id,
        )
        book.authors.append(author)
        db.add(book)
    db.commit()


def test_parse_stats_queries():
    assert assistant_stats.parse_stats_query("书库一共有多少本书")["metric"] == "overview"
    assert assistant_stats.parse_stats_query("阅读状态分布")["metric"] == "read_state"
    assert assistant_stats.parse_stats_query("作者排行前 3")["metric"] == "top"
    assert assistant_stats.parse_stats_query("按分类统计")["group_by"] == "category"
    assert assistant_stats.parse_stats_query("每个月新增多少本")["metric"] == "timeline"
    assert assistant_stats.parse_stats_query("今年买了多少书")["metric"] == "spend"
    assert assistant_stats.parse_stats_query("这本书讲了什么") is None


def test_stats_service_answers_overview_and_breakdowns(db):
    _seed(db)

    overview = _answer(db, "书库一共有多少本书")
    assert "3 本书" in overview["answer"]

    read_state = _answer(db, "阅读状态分布")
    assert read_state["table"]["columns"] == ["状态", "数量"]
    assert {row[0] for row in read_state["table"]["rows"]} == {"已读", "在读", "未读"}

    author = _answer(db, "作者排行前 3")
    assert author["table"]["rows"][0] == ["刘慈欣", 3]

    category = _answer(db, "按分类统计")
    assert ["科幻", 3] in category["table"]["rows"]


def test_stats_service_ignores_non_stats_questions(db):
    assert _answer(db, "红楼梦的作者是谁？") is None


def test_parse_this_month_purchase():
    from datetime import date

    query = assistant_stats.parse_stats_query("这个月买了多少书")
    assert query["metric"] == "spend_month"
    assert (query["year"], query["month"]) == (date.today().year, date.today().month)


def test_stats_service_this_month_purchase(db):
    from datetime import date

    _seed(db)
    book = db.query(Book).first()
    today = date.today()
    book.purchase_date = __import__("datetime").datetime(today.year, today.month, 1)
    book.purchase_price = 42.5
    db.commit()

    body = _answer(db, "这个月买了多少书")

    assert "购书 1 本" in body["answer"]
    assert "42.5" in body["answer"]


def test_parse_purchase_list_questions():
    from datetime import date

    query = assistant_stats.parse_stats_query("上个月买了些什么书")
    assert query["metric"] == "purchase_list"
    assert query["year"] == (date.today().year if date.today().month > 1 else date.today().year - 1)
    assert assistant_stats.parse_stats_query("上个月买了多少书")["metric"] == "spend_month"
    assert assistant_stats.parse_stats_query("今年买了哪些书")["metric"] == "purchase_list"


def test_purchase_list_returns_titles(db):
    from datetime import date, datetime

    _seed(db)
    book = db.query(Book).first()
    book.purchase_date = datetime(date.today().year, max(1, date.today().month - 1), 1) if date.today().month > 1 else datetime(date.today().year - 1, 12, 1)
    book.purchase_price = 42.5
    db.commit()

    body = _answer(db, "上个月买了些什么书")

    assert "《三体》" in body["answer"]
    assert body["table"] == {"columns": [], "rows": []}
    assert body["books"][0]["title"] == "三体"
    assert body["books"][0]["thumb_image"] is None or body["books"][0]["thumb_image"] == ""


def test_extreme_price_questions():
    assert assistant_stats.parse_stats_query("最贵的书是哪套") == {
        "metric": "extreme",
        "field": "price",
        "order": "max",
    }
    assert assistant_stats.parse_stats_query("最便宜的书")["order"] == "min"
    assert assistant_stats.parse_stats_query("评分最高的书")["field"] == "douban_score"


def test_extreme_price_service_returns_the_book(db):
    _seed(db)
    books = db.query(Book).order_by(Book.id).all()
    books[0].price = 99
    books[1].price = 120
    books[2].price = 300
    books[2].book_count = 3
    db.commit()

    body = _answer(db, "最贵的书是哪套")

    assert "《球状闪电》" in body["answer"]
    assert "300" in body["answer"]
    assert "3 册" in body["answer"]
    assert body["table"] == {"columns": [], "rows": []}
    assert body["books"][0]["title"] == "球状闪电"
