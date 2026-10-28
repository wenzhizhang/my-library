"""Small SQLite cache for external book metadata (shared by all users)."""

from __future__ import annotations

import json
import os
import sqlite3
import threading
import time
from pathlib import Path
from typing import Any

DEFAULT_TTL = 30 * 24 * 3600
_lock = threading.Lock()


def _cache_path() -> Path:
    explicit = os.environ.get("METADATA_CACHE_PATH")
    if explicit:
        path = Path(explicit)
    else:
        data_dir = os.environ.get("DATA_DIR", "/app/data")
        path = Path(data_dir) / "metadata_cache.db"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(_cache_path(), timeout=10)
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS metadata_cache (
            cache_key TEXT PRIMARY KEY,
            source TEXT NOT NULL,
            payload TEXT NOT NULL,
            fetched_at REAL NOT NULL,
            expires_at REAL NOT NULL
        )
        """
    )
    return conn


def cache_key(kind: str, value: str) -> str:
    return f"{kind}:{value.strip().casefold()}"


def get(key: str, *, now: float | None = None) -> dict[str, Any] | None:
    now = time.time() if now is None else now
    with _lock, _connect() as conn:
        row = conn.execute(
            "SELECT payload, expires_at FROM metadata_cache WHERE cache_key = ?",
            (key,),
        ).fetchone()
    if row is None:
        return None
    payload, expires_at = row
    if expires_at <= now:
        return None
    try:
        return json.loads(payload)
    except json.JSONDecodeError:
        return None


def set(key: str, source: str, payload: dict[str, Any], ttl: int = DEFAULT_TTL) -> None:
    now = time.time()
    with _lock, _connect() as conn:
        conn.execute(
            """
            INSERT INTO metadata_cache (cache_key, source, payload, fetched_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(cache_key) DO UPDATE SET
                source = excluded.source,
                payload = excluded.payload,
                fetched_at = excluded.fetched_at,
                expires_at = excluded.expires_at
            """,
            (key, source, json.dumps(payload, ensure_ascii=False), now, now + ttl),
        )
        conn.commit()


def clear() -> None:
    with _lock, _connect() as conn:
        conn.execute("DELETE FROM metadata_cache")
        conn.commit()
