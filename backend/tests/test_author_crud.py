"""Tests for /api/authors/ CRUD endpoints."""

import pytest
from auth import get_current_user_id
from models import Author


# ── Helpers ──────────────────────────────────────────────────────

AUTHOR_PAYLOAD = {
    "name": "Homer",
    "name_cn": "荷马",
    "nation": "希腊",
    "dynasty": "上古",
    "intro": "Ancient Greek poet",
}


def auth_override(user_id: int = 1):
    """Return dependency override that returns the given user_id."""
    return lambda: user_id


def no_auth_override():
    """Return dependency override that returns None (unauthenticated)."""
    return lambda: None


def create_author_via_db(db, **kwargs):
    """Insert an Author directly into the DB and return it."""
    payload = {**AUTHOR_PAYLOAD, **kwargs}
    author = Author(**payload)
    db.add(author)
    db.commit()
    db.refresh(author)
    return author


# ── Fixtures ─────────────────────────────────────────────────────

@pytest.fixture
def authed_client(client):
    """TestClient with a logged-in user (get_current_user_id → 1)."""
    app_deps = client.app.dependency_overrides
    app_deps[get_current_user_id] = auth_override()
    yield client
    app_deps.pop(get_current_user_id, None)


@pytest.fixture
def unauth_client(client):
    """TestClient with no user (get_current_user_id → None → 401)."""
    app_deps = client.app.dependency_overrides
    app_deps[get_current_user_id] = no_auth_override()
    yield client
    app_deps.pop(get_current_user_id, None)


# ── POST /api/authors/ ──────────────────────────────────────────

class TestCreateAuthor:
    ENDPOINT = "/api/authors/"

    def test_create_success(self, authed_client):
        """AUTH-03: POST create → 200."""
        resp = authed_client.post(self.ENDPOINT, json=AUTHOR_PAYLOAD)
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "Homer"
        assert data["name_cn"] == "荷马"
        assert data["nation"] == "希腊"
        assert data["dynasty"] == "上古"
        assert data["intro"] == "Ancient Greek poet"
        assert data["id"] > 0

    def test_create_unauthorized(self, unauth_client):
        """POST without auth → 401."""
        resp = unauth_client.post(self.ENDPOINT, json=AUTHOR_PAYLOAD)
        assert resp.status_code == 401
        assert "Login required" in resp.text

    def test_create_nation_无(self, authed_client):
        """AUTH-14: POST with nation='无' → 200."""
        payload = {**AUTHOR_PAYLOAD, "nation": "无"}
        resp = authed_client.post(self.ENDPOINT, json=payload)
        assert resp.status_code == 200
        assert resp.json()["nation"] == "无"

    def test_create_invalid_nation(self, authed_client):
        """AUTH-13: POST with invalid nation → 422."""
        payload = {**AUTHOR_PAYLOAD, "nation": "Atlantis"}
        resp = authed_client.post(self.ENDPOINT, json=payload)
        assert resp.status_code == 422

    def test_create_invalid_dynasty(self, authed_client):
        """POST with invalid dynasty → 422."""
        payload = {**AUTHOR_PAYLOAD, "dynasty": "Future"}
        resp = authed_client.post(self.ENDPOINT, json=payload)
        assert resp.status_code == 422

    def test_create_missing_name(self, authed_client):
        """POST without required name field → 422."""
        payload = {k: v for k, v in AUTHOR_PAYLOAD.items() if k != "name"}
        resp = authed_client.post(self.ENDPOINT, json=payload)
        assert resp.status_code == 422

    def test_create_default_nation(self, authed_client):
        """POST without nation uses default '无'."""
        payload = {k: v for k, v in AUTHOR_PAYLOAD.items() if k != "nation"}
        resp = authed_client.post(self.ENDPOINT, json=payload)
        assert resp.status_code == 200
        assert resp.json()["nation"] == "无"


# ── GET /api/authors/ (list + search) ───────────────────────────

class TestListAuthors:
    ENDPOINT = "/api/authors/"

    def test_list_empty(self, client):
        """GET without any authors → empty list."""
        resp = client.get(self.ENDPOINT)
        assert resp.status_code == 200
        data = resp.json()
        assert data["authors"] == []
        assert data["total_authors"] == 0
        assert data["total_pages"] == 0

    def test_list_with_authors(self, client, db):
        """GET returns created authors."""
        create_author_via_db(db, name="Plato", name_cn="柏拉图", nation="希腊")
        create_author_via_db(db, name="Aristotle", name_cn="亚里士多德", nation="希腊")
        resp = client.get(self.ENDPOINT)
        assert resp.status_code == 200
        data = resp.json()
        assert data["total_authors"] == 2
        names = [a["name"] for a in data["authors"]]
        assert "Aristotle" in names
        assert "Plato" in names

    def test_search_by_name(self, client, db):
        """AUTH-02: GET with ?q= filters by name (case-insensitive)."""
        create_author_via_db(db, name="Confucius", name_cn="孔子", nation="中国")
        create_author_via_db(db, name="Mencius", name_cn="孟子", nation="中国")
        resp = client.get(self.ENDPOINT, params={"q": "conf"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["total_authors"] == 1
        assert data["authors"][0]["name"] == "Confucius"

    def test_search_by_name_cn(self, client, db):
        """AUTH-02: GET with ?q= filters by name_cn."""
        create_author_via_db(db, name="Zhuangzi", name_cn="庄子", nation="中国")
        create_author_via_db(db, name="Laozi", name_cn="老子", nation="中国")
        resp = client.get(self.ENDPOINT, params={"q": "庄"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["total_authors"] == 1
        assert data["authors"][0]["name_cn"] == "庄子"

    def test_search_no_match(self, client, db):
        """GET with ?q= returning no results."""
        create_author_via_db(db, name="Socrates", name_cn="苏格拉底", nation="希腊")
        resp = client.get(self.ENDPOINT, params={"q": "nonexistent"})
        assert resp.status_code == 200
        assert resp.json()["total_authors"] == 0

    def test_pagination(self, client, db):
        """GET with page/limit parameters."""
        for i in range(5):
            create_author_via_db(db, name=f"Author {i}", name_cn=f"作者{i}", nation="中国")
        resp = client.get(self.ENDPOINT, params={"page": 1, "limit": 2})
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["authors"]) == 2
        assert data["total_authors"] == 5
        assert data["total_pages"] == 3


# ── GET /api/authors/{id} (single) ──────────────────────────────

class TestGetAuthor:
    ENDPOINT = "/api/authors/{author_id}"

    def test_get_success(self, client, db):
        """GET by existing id → 200."""
        author = create_author_via_db(db, name="Sappho", name_cn="萨福", nation="希腊")
        resp = client.get(self.ENDPOINT.format(author_id=author.id))
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "Sappho"
        assert data["id"] == author.id

    def test_get_not_found(self, client):
        """GET by non-existent id → 404."""
        resp = client.get(self.ENDPOINT.format(author_id=9999))
        assert resp.status_code == 404


# ── PUT /api/authors/{id} ───────────────────────────────────────

class TestUpdateAuthor:
    ENDPOINT = "/api/authors/{author_id}"

    def test_update_success(self, authed_client, db):
        """AUTH-05: PUT update → 200."""
        author = create_author_via_db(db, name="Old Name", name_cn="旧名", nation="中国")
        resp = authed_client.put(
            self.ENDPOINT.format(author_id=author.id),
            json={"name": "New Name"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "New Name"
        assert data["id"] == author.id

    def test_update_unauthorized(self, unauth_client, db):
        """PUT without auth → 401."""
        author = create_author_via_db(db, name="Test", name_cn="测试", nation="中国")
        resp = unauth_client.put(
            self.ENDPOINT.format(author_id=author.id),
            json={"name": "Hacked"},
        )
        assert resp.status_code == 401

    def test_update_not_found(self, authed_client):
        """PUT non-existent author → 404."""
        resp = authed_client.put(
            self.ENDPOINT.format(author_id=9999),
            json={"name": "Ghost"},
        )
        assert resp.status_code == 404

    def test_update_partial(self, authed_client, db):
        """PUT only changes specified fields."""
        author = create_author_via_db(db, name="Original", name_cn="原始",
                                        nation="希腊", dynasty="上古")
        resp = authed_client.put(
            self.ENDPOINT.format(author_id=author.id),
            json={"dynasty": "当代"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "Original"
        assert data["nation"] == "希腊"
        assert data["dynasty"] == "当代"

    def test_update_invalid_nation(self, authed_client, db):
        """PUT with invalid nation → 422."""
        author = create_author_via_db(db, name="Valid", name_cn="有效", nation="中国")
        resp = authed_client.put(
            self.ENDPOINT.format(author_id=author.id),
            json={"nation": "Invalid"},
        )
        assert resp.status_code == 422


# ── DELETE /api/authors/{id} ────────────────────────────────────

class TestDeleteAuthor:
    ENDPOINT = "/api/authors/{author_id}"

    def test_delete_success(self, authed_client, db):
        """AUTH-06: DELETE → 200."""
        author = create_author_via_db(db, name="Delete Me", name_cn="删我", nation="日本")
        resp = authed_client.delete(self.ENDPOINT.format(author_id=author.id))
        assert resp.status_code == 200
        assert resp.json()["message"] == "Author deleted"
        # Verify it's actually gone
        assert db.query(Author).filter(Author.id == author.id).first() is None

    def test_delete_unauthorized(self, unauth_client, db):
        """DELETE without auth → 401."""
        author = create_author_via_db(db, name="Protected", name_cn="受保护", nation="中国")
        resp = unauth_client.delete(self.ENDPOINT.format(author_id=author.id))
        assert resp.status_code == 401

    def test_delete_not_found(self, authed_client):
        """DELETE non-existent author → 404."""
        resp = authed_client.delete(self.ENDPOINT.format(author_id=9999))
        assert resp.status_code == 404


# ── GET /api/authors/nations & dynasties ────────────────────────

class TestNationsAndDynasties:
    def test_get_nations(self, client):
        resp = client.get("/api/authors/nations")
        assert resp.status_code == 200
        data = resp.json()
        assert "nations" in data
        assert "无" in data["nations"]
        assert "希腊" in data["nations"]

    def test_get_dynasties(self, client):
        resp = client.get("/api/authors/dynasties")
        assert resp.status_code == 200
        data = resp.json()
        assert "dynasties" in data
        assert "上古" in data["dynasties"]
        assert "当代" in data["dynasties"]
