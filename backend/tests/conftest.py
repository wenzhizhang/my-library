"""Shared fixtures for backend tests."""

import os
import sys
from pathlib import Path

# Point database files to a writable temp dir (default /app/data may not exist)
os.environ.setdefault("DATA_DIR", "/tmp")

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

# Ensure backend module is importable
sys.path.insert(0, str(Path(__file__).parent.parent))

from database import Base, get_db, get_stats_db
from main import app  # noqa: E402


@pytest.fixture(scope="session")
def engine():
    """SQLite in-memory engine."""
    return create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})


@pytest.fixture(scope="session")
def tables(engine):
    """Create all tables once per session."""
    Base.metadata.create_all(engine)
    yield
    Base.metadata.drop_all(engine)


@pytest.fixture
def db(engine, tables):
    """Fresh DB session per test (rolled back after)."""
    connection = engine.connect()
    transaction = connection.begin()
    session = Session(bind=connection)

    # Import all models so they're registered with Base
    import models  # noqa: F401

    yield session

    session.close()
    transaction.rollback()
    connection.close()


@pytest.fixture
def client(db):
    """FastAPI TestClient with DB override."""
    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_stats_db] = lambda: db
    from fastapi.testclient import TestClient
    with TestClient(app) as c:
        yield c
    app.dependency_overrides = {}


@pytest.fixture(autouse=True)
def _signed_in_by_default():
    """Stand in for the bearer token.

    The spec marks the write endpoints as requiring BearerAuth and the routers enforce it, so an
    unauthenticated request is a 401. Most tests here are about what an endpoint does with its
    payload, not about the token, so this overrides the dependency to a fixed user.
    tests/test_auth_required.py removes the override to pin the 401 contract itself, and anything
    that is genuinely about being anonymous asks for the `guest` client below.

    The id is deliberately high: registered users auto-increment from 1, and a stand-in that
    collided with one would silently answer as that user.
    """
    from auth import get_current_user_id, require_user_id
    from fastapi import Depends
    from main import app

    async def _require_user_id_or_stand_in(user_id: int = Depends(get_current_user_id)):
        """The real user when the test sent a token, a stand-in when it did not.

        Resolving through get_current_user_id keeps the write path and the read path on the same
        identity, so a test that registers a user and then reads its own setting still sees it.
        """
        return user_id if user_id is not None else 10_000

    app.dependency_overrides[require_user_id] = _require_user_id_or_stand_in
    yield
    app.dependency_overrides.pop(require_user_id, None)


@pytest.fixture
def guest(client):
    """A client with no token at all, for the tests that are about being anonymous."""
    from auth import require_user_id
    from main import app

    app.dependency_overrides.pop(require_user_id, None)
    return client
