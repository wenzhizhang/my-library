"""Author string parsing and name+metadata matching."""

from models import Author
from services.author_parser import parse_author_string
from services.author_resolver import resolve_author_ids


def test_parses_dynasty_role_and_translator():
    refs = parse_author_string("（南朝宋）刘义庆 著；张撝之 译注")

    assert [(ref.name, ref.nation, ref.dynasty) for ref in refs] == [
        ("刘义庆", "中国", "南朝宋"),
        ("张撝之", "", ""),
    ]


def test_strips_role_without_a_space_on_a_long_name():
    refs = parse_author_string("刘义庆著；王著")
    assert [ref.name for ref in refs] == ["刘义庆", "王著"]


def test_parses_nationality_brackets():
    refs = parse_author_string("[宋] 苏轼；（日）东野圭吾；【美】海明威")

    assert refs[0].dynasty == "宋"
    assert refs[0].nation == "中国"
    assert refs[1].nation == "日本"
    assert refs[2].nation == "美国"


def test_keeps_names_and_splits_contributors():
    refs = parse_author_string("张三 著；李四 译；王五 校注；J.K. 罗琳；王著")

    assert [ref.name for ref in refs] == ["张三", "李四", "王五", "J.K. 罗琳", "王著"]


def test_reuses_author_when_metadata_matches(db):
    existing = Author(
        name="刘义庆", name_cn="刘义庆", nation="中国", dynasty="南朝宋"
    )
    db.add(existing)
    db.commit()

    ids = resolve_author_ids(db, ["（南朝宋）刘义庆 著；张撝之 译注"])

    assert len(ids) == 2
    assert ids[0] == existing.id
    assert db.query(Author).count() == 2
    translator = db.query(Author).filter(Author.id == ids[1]).one()
    assert translator.nation == "中国"
    assert translator.dynasty == "当代"


def test_creates_author_when_dynasty_differs(db):
    existing = Author(name="刘义庆", name_cn="刘义庆", nation="中国", dynasty="宋")
    db.add(existing)
    db.commit()

    ids = resolve_author_ids(db, ["（南朝宋）刘义庆 著"])

    assert ids[0] != existing.id
    created = db.query(Author).filter(Author.id == ids[0]).one()
    assert created.dynasty == "南朝宋"
    assert db.query(Author).count() == 2


def test_fills_unknown_nation_instead_of_duplicating(db):
    existing = Author(name="东野圭吾", name_cn="东野圭吾", nation="无")
    db.add(existing)
    db.commit()

    ids = resolve_author_ids(db, ["（日）东野圭吾 著"])

    assert ids == [existing.id]
    assert existing.nation == "日本"
    assert db.query(Author).count() == 1


def test_foreign_author_has_no_dynasty(db):
    ids = resolve_author_ids(db, ["（日）东野圭吾 著"])
    author = db.query(Author).filter(Author.id == ids[0]).one()
    assert author.nation == "日本"
    assert author.dynasty is None


def test_foreign_author_loses_stale_dynasty(db):
    existing = Author(
        name="东野圭吾", name_cn="东野圭吾", nation="日本", dynasty="江户"
    )
    db.add(existing)
    db.commit()

    ids = resolve_author_ids(db, ["（日）东野圭吾 著"])

    assert ids == [existing.id]
    assert existing.dynasty is None


def test_unknown_bracket_falls_back_to_china_contemporary():
    from services.author_parser import AuthorRef, resolved_author

    refs = parse_author_string("（未知朝代）张三")
    assert refs[0].nation == ""
    assert refs[0].dynasty == ""
    resolved = resolved_author(refs[0])
    assert (resolved.nation, resolved.dynasty) == ("中国", "当代")


def test_foreign_resolved_author_has_empty_dynasty():
    from services.author_parser import AuthorRef, resolved_author

    resolved = resolved_author(AuthorRef("东野圭吾", "日本", "江户"))
    assert resolved.nation == "日本"
    assert resolved.dynasty == ""


def test_root_candidate_with_unknown_nation_uses_defaults(db):
    from services.author_parser import AuthorRef
    from services.author_resolver import copy_author_from_candidate

    candidate = Author(name="某人", name_cn="某人", nation="无")
    db.add(candidate)
    db.commit()

    copied = copy_author_from_candidate(db, candidate, AuthorRef("某人"))

    assert copied.nation == "中国"
    assert copied.dynasty == "当代"


def test_parses_foreign_translated_names_with_original_name():
    refs = parse_author_string(
        "(美) 约翰·P.丹吉洛， [美]罗伯特·布利策，"
        "[英] 贝拉・博洛巴斯（Béla Bollobás）"
    )

    assert [(ref.name, ref.nation) for ref in refs] == [
        ("约翰·P.丹吉洛", "美国"),
        ("罗伯特·布利策", "美国"),
        ("贝拉・博洛巴斯", "英国"),
    ]
    assert refs[2].original_name == "Béla Bollobás"
    assert all(ref.dynasty == "" for ref in refs)


def test_creates_author_with_original_name(db):
    ids = resolve_author_ids(db, ["[英] 贝拉・博洛巴斯（Béla Bollobás）"])
    author = db.query(Author).filter(Author.id == ids[0]).one()
    assert author.name == "Béla Bollobás"
    assert author.name_cn == "贝拉・博洛巴斯"
