"""POST /api/export/sync-to-root — the response separates counts from the sync's diagnostics.

The aggregator returns ``{table: {total, synced, failed}}`` plus ``_diagnostics`` and, when rows
failed, ``_errors``. Those used to travel inside ``counts``, which made the response a
heterogeneous map: a schema (and therefore any generated client) can describe count objects or
arbitrary values, not both, so a failed sync could not be decoded at all. They are their own
fields now, and this pins that.
"""


def test_sync_to_root_keeps_counts_and_diagnostics_apart(client, monkeypatch):
    """counts carries only count objects; diagnostics and errors sit beside it."""
    # The route requires a user; this test is about the response shape, so stand in for the token.
    from auth import require_user_id
    from main import app

    app.dependency_overrides[require_user_id] = lambda: 1

    def fake_sync(db, differential=True):
        return {
            "books": {"total": 3, "synced": 2, "failed": 1},
            "authors": {"total": 1, "synced": 1},
            "_diagnostics": {"root_db_path": "sqlite:///root.db", "root_db_tables": ["books"]},
            "_errors": ["book 7: disk full"],
        }

    monkeypatch.setattr("routers.export.sync_all_to_root", fake_sync)

    response = client.post("/api/export/sync-to-root", params={"differential": "false"})

    assert response.status_code == 200
    body = response.json()
    assert body["differential"] is False
    assert body["counts"] == {
        "books": {"total": 3, "synced": 2, "failed": 1},
        "authors": {"total": 1, "synced": 1},
    }
    assert body["diagnostics"] == {
        "root_db_path": "sqlite:///root.db",
        "root_db_tables": ["books"],
    }
    assert body["errors"] == ["book 7: disk full"]


def test_sync_to_root_reports_no_errors_when_none_failed(client, monkeypatch):
    """A clean sync answers with errors: null rather than omitting the key."""
    from auth import require_user_id
    from main import app

    app.dependency_overrides[require_user_id] = lambda: 1

    monkeypatch.setattr(
        "routers.export.sync_all_to_root",
        lambda db, differential=True: {"books": {"total": 1, "synced": 1}, "_diagnostics": {}},
    )

    body = client.post("/api/export/sync-to-root").json()

    assert body["counts"] == {"books": {"total": 1, "synced": 1}}
    assert body["errors"] is None
    assert body["diagnostics"] == {}
