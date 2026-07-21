import os
from typing import Optional

from fastapi import Depends
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, Session
from models import Base

# ── sqlite-vec extension loader ──────────────────────────────────────────────

import sqlite_vec


def _load_vec_extension(dbapi_conn, _connection_record):
    """Load sqlite-vec extension onto the raw DB-API connection."""
    dbapi_conn.enable_load_extension(True)
    sqlite_vec.load(dbapi_conn)
    dbapi_conn.enable_load_extension(False)


def _create_sqlite_engine(db_path: str, **kwargs):
    """Create a SQLite engine with sqlite-vec loaded on every connection."""
    engine = create_engine(f"sqlite:///{db_path}", **kwargs)
    event.listen(engine, "connect", _load_vec_extension)
    return engine


DATA_DIR = os.environ.get("DATA_DIR", "/app/data")

# =============================================================================
# Global auth database
# =============================================================================
AUTH_DB_PATH = os.path.join(DATA_DIR, "auth.db")
auth_engine = _create_sqlite_engine(
    AUTH_DB_PATH, connect_args={"check_same_thread": False}
)
AuthSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=auth_engine)


def init_auth_db():
    from models.user import User  # noqa: F401

    Base.metadata.create_all(bind=auth_engine, tables=[User.__table__])


def get_auth_db():
    db = AuthSessionLocal()
    try:
        yield db
    finally:
        db.close()


# =============================================================================
# User-specific databases (UUID.db) + demo db
# =============================================================================
_engines: dict[str, object] = {}


def _get_or_create_engine(db_path: str):
    if db_path not in _engines:
        engine = _create_sqlite_engine(
            db_path, connect_args={"check_same_thread": False}
        )
        Base.metadata.create_all(bind=engine)
        _engines[db_path] = engine

        # Initialise RAG tables (vector + FTS5) for this user's database
        from rag.vector_store import ensure_rag_tables

        with Session(engine) as session:
            ensure_rag_tables(session)

    return _engines[db_path]


def init_user_db(uuid: str) -> str:
    """Ensure a user's database file exists (empty, schema created by engine)."""
    user_db = os.path.join(DATA_DIR, f"{uuid}.db")
    if not os.path.exists(user_db):
        open(user_db, "a").close()
    return user_db


# =============================================================================
# FastAPI dependency - auto-routes to user DB or demo DB
# =============================================================================


def _resolve_db(uuid: Optional[str]) -> str:
    if uuid:
        return init_user_db(uuid)
    return os.path.join(DATA_DIR, "demo.db")


# AUTO-RESOLVING VERSION
from auth import get_current_user_uuid


def _get_db_auto(uuid: Optional[str] = Depends(get_current_user_uuid)):
    db_path = _resolve_db(uuid)
    engine = _get_or_create_engine(db_path)
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


get_db = _get_db_auto

# =============================================================================
# Stats database
# =============================================================================
STATS_DB_PATH = os.path.join(DATA_DIR, "stats.db")
stats_engine = create_engine(
    f"sqlite:///{STATS_DB_PATH}", connect_args={"check_same_thread": False}
)
StatsSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=stats_engine)


def init_stats_db():
    from models.stats import VisitLog  # noqa: F401

    Base.metadata.create_all(bind=stats_engine, tables=[VisitLog.__table__])


def get_stats_db():
    db = StatsSessionLocal()
    try:
        yield db
    finally:
        db.close()


# =============================================================================
# Applications database (shared)
# =============================================================================
APPS_DB_PATH = os.path.join(DATA_DIR, "applications.db")
apps_engine = create_engine(
    f"sqlite:///{APPS_DB_PATH}", connect_args={"check_same_thread": False}
)
AppsSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=apps_engine)


def init_apps_db():
    from models.application import Application  # noqa: F401

    Base.metadata.create_all(bind=apps_engine, tables=[Application.__table__])


def get_applications_db():
    db = AppsSessionLocal()
    try:
        yield db
    finally:
        db.close()


init_auth_db()
init_stats_db()
init_apps_db()

# =============================================================================
# Shared root database — public reference data (authors, publishers, etc.)
# =============================================================================
ROOT_DB_PATH = os.environ.get("ROOT_DB_PATH", os.path.join(DATA_DIR, "root.db"))
root_engine = create_engine(
    f"sqlite:///{ROOT_DB_PATH}", connect_args={"check_same_thread": False}
)
RootSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=root_engine)


def get_root_db():
    db = RootSessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_root_db():
    wanted = {"authors", "publishers", "brands", "book_series", "categories", "books", "book_authors"}

    print(f"[init_root_db] Path: {ROOT_DB_PATH}", flush=True)

    # Delegate table creation to sync service (CREATE TABLE IF NOT EXISTS)
    from services.sync_to_root import _ensure_tables
    _ensure_tables()

    # Drop any tables not in the wanted set (cleanup from old schemas)
    with root_engine.begin() as conn:
        tables = conn.exec_driver_sql(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        ).fetchall()
        for (name,) in tables:
            if name not in wanted:
                conn.exec_driver_sql(f"DROP TABLE IF EXISTS [{name}]")

    print(f"[init_root_db] Done, tables: {wanted}", flush=True)
