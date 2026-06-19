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
