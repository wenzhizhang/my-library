"""Tests for background list + per-user background selection.

Covers:
- GET /api/backgrounds            (config-driven list)
- GET /api/backgrounds/me         (guest → null, user → saved id)
- PUT /api/backgrounds/me         (auth required, validates id, per-user)
"""

from auth import create_access_token
from database import get_auth_db
from main import app
from models import User


def _register_user(db, username="alice", uuid="user-abc"):
    user = User(username=username, password_hash="x", uuid=uuid)
    db.add(user)
    db.flush()
    return user


def _auth_headers(user):
    token = create_access_token({"sub": str(user.id), "uuid": user.uuid})
    return {"Authorization": f"Bearer {token}"}


class TestBackgroundList:
    """GET /api/backgrounds"""

    def test_returns_default_and_items(self, client):
        # The endpoint is config-driven (hot-reload), so the expectation is
        # derived from the config file rather than hardcoded.
        import json
        import os

        config_path = os.path.join(
            os.path.dirname(__file__), "..", "config", "backgrounds.json"
        )
        with open(config_path, encoding="utf-8") as f:
            cfg = json.load(f)

        resp = client.get("/api/backgrounds")
        assert resp.status_code == 200
        data = resp.json()
        assert data["default_id"] == cfg["default"]
        ids = [b["id"] for b in data["backgrounds"]]
        assert cfg["default"] in ids
        assert len(ids) >= 1
        for b in data["backgrounds"]:
            assert b["id"]
            assert b["name"]
            assert b["url"].startswith("http")


class TestBackgroundSelection:
    """GET/PUT /api/backgrounds/me"""

    def test_guest_gets_null(self, client):
        resp = client.get("/api/backgrounds/me")
        assert resp.status_code == 200
        assert resp.json()["background_id"] is None

    def test_guest_cannot_save(self, client):
        resp = client.put("/api/backgrounds/me", json={"background_id": "bg2"})
        assert resp.status_code == 401

    def test_user_save_then_read(self, client, db):
        app.dependency_overrides[get_auth_db] = lambda: db
        user = _register_user(db)
        headers = _auth_headers(user)

        # Initially no selection
        resp = client.get("/api/backgrounds/me", headers=headers)
        assert resp.json()["background_id"] is None

        # Save a choice
        resp = client.put(
            "/api/backgrounds/me", json={"background_id": "bg5"}, headers=headers
        )
        assert resp.status_code == 200
        assert resp.json()["background_id"] == "bg5"

        # Read it back
        resp = client.get("/api/backgrounds/me", headers=headers)
        assert resp.json()["background_id"] == "bg5"

        # Update the choice
        resp = client.put(
            "/api/backgrounds/me", json={"background_id": "bg3"}, headers=headers
        )
        assert resp.status_code == 200
        resp = client.get("/api/backgrounds/me", headers=headers)
        assert resp.json()["background_id"] == "bg3"

    def test_users_are_isolated_and_guests_unaffected(self, client, db):
        app.dependency_overrides[get_auth_db] = lambda: db
        alice = _register_user(db, username="alice", uuid="user-alice")
        bob = _register_user(db, username="bob", uuid="user-bob")

        client.put(
            "/api/backgrounds/me", json={"background_id": "bg5"}, headers=_auth_headers(alice)
        )
        client.put(
            "/api/backgrounds/me", json={"background_id": "bg6"}, headers=_auth_headers(bob)
        )

        resp = client.get("/api/backgrounds/me", headers=_auth_headers(alice))
        assert resp.json()["background_id"] == "bg5"
        resp = client.get("/api/backgrounds/me", headers=_auth_headers(bob))
        assert resp.json()["background_id"] == "bg6"

        # Guests still see the default
        resp = client.get("/api/backgrounds/me")
        assert resp.status_code == 200
        assert resp.json()["background_id"] is None

    def test_unknown_background_rejected(self, client, db):
        app.dependency_overrides[get_auth_db] = lambda: db
        user = _register_user(db)
        resp = client.put(
            "/api/backgrounds/me", json={"background_id": "nope"}, headers=_auth_headers(user)
        )
        assert resp.status_code == 400
        assert "Unknown background" in resp.json()["detail"]

    def test_empty_background_id_rejected(self, client, db):
        app.dependency_overrides[get_auth_db] = lambda: db
        user = _register_user(db)
        resp = client.put(
            "/api/backgrounds/me", json={"background_id": "  "}, headers=_auth_headers(user)
        )
        assert resp.status_code == 400


class TestBackgroundConfigResilience:
    """Behavior when config/backgrounds.json is missing or transiently broken."""

    def test_missing_config_serves_fallback(self, client, monkeypatch):
        import routers.background as bg

        monkeypatch.setattr(bg, "CONFIG_DIR", "/nonexistent-config-dir")
        monkeypatch.setattr(bg, "_loaded_config", None)

        resp = client.get("/api/backgrounds")
        assert resp.status_code == 200
        data = resp.json()
        assert data["default_id"] == "bg4"
        assert any(b["id"] == "bg4" for b in data["backgrounds"])

    def test_last_known_good_served_on_transient_failure(
        self, client, monkeypatch, tmp_path
    ):
        import json

        import routers.background as bg

        cfg = tmp_path / "backgrounds.json"
        cfg.write_text(
            json.dumps(
                {
                    "default": "bg5",
                    "backgrounds": [
                        {"id": "bg5", "name": "五", "url": "https://x/bg5.jpg"}
                    ],
                }
            ),
            encoding="utf-8",
        )
        monkeypatch.setattr(bg, "CONFIG_DIR", str(tmp_path))
        monkeypatch.setattr(bg, "_loaded_config", None)

        resp = client.get("/api/backgrounds")
        assert resp.status_code == 200
        assert resp.json()["default_id"] == "bg5"

        # Non-atomic edit leaves the file malformed — last-known-good still served
        cfg.write_text("{broken", encoding="utf-8")
        resp = client.get("/api/backgrounds")
        assert resp.status_code == 200
        assert resp.json()["default_id"] == "bg5"
