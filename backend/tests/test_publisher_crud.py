"""Tests for /api/publishers/ endpoints."""

from auth import get_current_user_id
from main import app
from models import Publisher


def _auth(user_id: int = 1):
    """Override get_current_user_id for authenticated requests."""
    app.dependency_overrides[get_current_user_id] = lambda: user_id


# ---------------------------------------------------------------------------
# CREATE
# ---------------------------------------------------------------------------

class TestCreatePublisher:
    def test_requires_auth(self, client):
        """POST without auth token -> 401."""
        resp = client.post("/api/publishers/", json={"name": "Unauth Press"})
        assert resp.status_code == 401

    def test_create(self, client, db):
        """POST with auth -> 200 and record in DB."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        resp = client.post("/api/publishers/", json={"name": "Test Press", "intro": "A test publisher"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "Test Press"
        assert data["intro"] == "A test publisher"
        assert data["id"] is not None

        # Verify in DB
        pub = db.query(Publisher).filter(Publisher.id == data["id"]).first()
        assert pub is not None
        assert pub.name == "Test Press"

    def test_create_minimal(self, client, db):
        """POST with only required fields."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        resp = client.post("/api/publishers/", json={"name": "Minimal Press"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "Minimal Press"
        assert data["intro"] is None
        assert data["logo"] is None


# ---------------------------------------------------------------------------
# READ (list)
# ---------------------------------------------------------------------------

class TestReadPublishers:
    def test_empty_list(self, client):
        """GET without data returns empty list."""
        resp = client.get("/api/publishers/")
        assert resp.status_code == 200
        data = resp.json()
        assert data["publishers"] == []
        assert data["total_publishers"] == 0
        assert data["total_pages"] == 0

    def test_list_with_publishers(self, client, db):
        """GET returns stored publishers."""
        db.add_all([
            Publisher(name="Alpha Press"),
            Publisher(name="Beta Books"),
            Publisher(name="Gamma Publishing"),
        ])
        db.commit()

        resp = client.get("/api/publishers/")
        assert resp.status_code == 200
        data = resp.json()
        names = [p["name"] for p in data["publishers"]]
        assert names == ["Alpha Press", "Beta Books", "Gamma Publishing"]
        assert data["total_publishers"] == 3
        assert data["total_pages"] == 1

    def test_search_by_q(self, client, db):
        """GET with ?q= filters by name (case-insensitive)."""
        db.add_all([
            Publisher(name="Alpha Press"),
            Publisher(name="Beta Books"),
            Publisher(name="Gamma Publishing"),
        ])
        db.commit()

        resp = client.get("/api/publishers/?q=beta")
        assert resp.status_code == 200
        data = resp.json()
        names = [p["name"] for p in data["publishers"]]
        assert names == ["Beta Books"]
        assert data["total_publishers"] == 1

        # Case-insensitive match
        resp = client.get("/api/publishers/?q=ALPHA")
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["publishers"]) == 1
        assert data["publishers"][0]["name"] == "Alpha Press"

    def test_search_no_match(self, client, db):
        """GET with ?q= that matches nothing returns empty list."""
        db.add(Publisher(name="Only Press"))
        db.commit()

        resp = client.get("/api/publishers/?q=nonexistent")
        assert resp.status_code == 200
        data = resp.json()
        assert data["publishers"] == []
        assert data["total_publishers"] == 0

    def test_pagination(self, client, db):
        """GET respects page and limit."""
        for i in range(5):
            db.add(Publisher(name=f"Publisher {i}"))
        db.commit()

        # page 1, limit 2 -> first 2
        resp = client.get("/api/publishers/?page=1&limit=2")
        data = resp.json()
        assert len(data["publishers"]) == 2
        assert data["publishers"][0]["name"] == "Publisher 0"
        assert data["publishers"][1]["name"] == "Publisher 1"
        assert data["total_publishers"] == 5
        assert data["total_pages"] == 3

        # page 3, limit 2 -> last item
        resp = client.get("/api/publishers/?page=3&limit=2")
        data = resp.json()
        assert len(data["publishers"]) == 1
        assert data["publishers"][0]["name"] == "Publisher 4"

    def test_list_no_auth_required(self, client):
        """GET does not require authentication."""
        resp = client.get("/api/publishers/")
        assert resp.status_code == 200


# ---------------------------------------------------------------------------
# READ (single)
# ---------------------------------------------------------------------------

class TestReadPublisher:
    def test_get_by_id(self, client, db):
        """GET /api/publishers/{id} returns the publisher."""
        pub = Publisher(name="Single Press", intro="Just one")
        db.add(pub)
        db.commit()
        db.refresh(pub)

        resp = client.get(f"/api/publishers/{pub.id}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["id"] == pub.id
        assert data["name"] == "Single Press"
        assert data["intro"] == "Just one"

    def test_not_found(self, client):
        """GET /api/publishers/{id} with unknown id -> 404."""
        resp = client.get("/api/publishers/99999")
        assert resp.status_code == 404
        assert resp.json()["detail"] == "Publisher not found"


# ---------------------------------------------------------------------------
# UPDATE
# ---------------------------------------------------------------------------

class TestUpdatePublisher:
    def test_requires_auth(self, client, db):
        """PUT without auth -> 401."""
        pub = Publisher(name="To Update")
        db.add(pub)
        db.commit()
        db.refresh(pub)

        resp = client.put(f"/api/publishers/{pub.id}", json={"name": "Hacked"})
        assert resp.status_code == 401

    def test_update(self, client, db):
        """PUT updates fields."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        pub = Publisher(name="Old Name", intro="Old intro")
        db.add(pub)
        db.commit()
        db.refresh(pub)

        resp = client.put(
            f"/api/publishers/{pub.id}",
            json={"name": "New Name", "intro": "New intro", "logo": "http://logo.new"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "New Name"
        assert data["intro"] == "New intro"
        assert data["logo"] == "http://logo.new"

        # Verify persisted
        db.refresh(pub)
        assert pub.name == "New Name"
        assert pub.intro == "New intro"

    def test_update_partial(self, client, db):
        """PUT with only one field leaves others unchanged."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        pub = Publisher(name="Partial", intro="Will stay")
        db.add(pub)
        db.commit()
        db.refresh(pub)

        resp = client.put(f"/api/publishers/{pub.id}", json={"name": "Changed"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["name"] == "Changed"
        assert data["intro"] == "Will stay"

    def test_update_not_found(self, client):
        """PUT on non-existent id -> 404."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        resp = client.put("/api/publishers/99999", json={"name": "Ghost"})
        assert resp.status_code == 404
        assert resp.json()["detail"] == "Publisher not found"


# ---------------------------------------------------------------------------
# DELETE
# ---------------------------------------------------------------------------

class TestDeletePublisher:
    def test_requires_auth(self, client, db):
        """DELETE without auth -> 401."""
        pub = Publisher(name="To Delete")
        db.add(pub)
        db.commit()
        db.refresh(pub)

        resp = client.delete(f"/api/publishers/{pub.id}")
        assert resp.status_code == 401

    def test_delete(self, client, db):
        """DELETE removes the publisher."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        pub = Publisher(name="Delete Me")
        db.add(pub)
        db.commit()
        db.refresh(pub)
        pub_id = pub.id

        resp = client.delete(f"/api/publishers/{pub_id}")
        assert resp.status_code == 200
        assert resp.json()["message"] == "Publisher deleted"

        # Verify gone from DB
        assert db.query(Publisher).filter(Publisher.id == pub_id).first() is None

    def test_delete_not_found(self, client):
        """DELETE on non-existent id -> 404."""
        app.dependency_overrides[get_current_user_id] = lambda: 1
        resp = client.delete("/api/publishers/99999")
        assert resp.status_code == 404
        assert resp.json()["detail"] == "Publisher not found"
