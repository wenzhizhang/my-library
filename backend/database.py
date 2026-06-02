import os
from typing import Optional

from fastapi import Depends
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from models import Base

DATA_DIR = os.environ.get("DATA_DIR", "/app/data")

# =============================================================================
# Global auth database
# =============================================================================
AUTH_DB_PATH = os.path.join(DATA_DIR, "auth.db")
auth_engine = create_engine(f"sqlite:///{AUTH_DB_PATH}", connect_args={"check_same_thread": False})
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
        engine = create_engine(f"sqlite:///{db_path}", connect_args={"check_same_thread": False})
        Base.metadata.create_all(bind=engine)
        _engines[db_path] = engine
    return _engines[db_path]


def init_user_db(uuid: str) -> str:
    """Ensure a user's database file exists (empty, schema created by engine)."""
    user_db = os.path.join(DATA_DIR, f"{uuid}.db")
    if not os.path.exists(user_db):
        open(user_db, "a").close()
    return user_db


# =============================================================================
# FastAPI dependency — auto-routes to user DB or demo DB
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
stats_engine = create_engine(f"sqlite:///{STATS_DB_PATH}", connect_args={"check_same_thread": False})
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


# Initialize on import
init_auth_db()
init_stats_db()
