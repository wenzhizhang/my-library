from sqlalchemy import Column, Integer, String, DateTime
from datetime import datetime

from .base import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(100), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    uuid = Column(String(36), unique=True, nullable=False, default=lambda: __import__('uuid').uuid4().hex)
    created_at = Column(DateTime, default=datetime.now)


class UserSetting(Base):
    """Per-user UI preferences (e.g. chosen background image id)."""

    __tablename__ = "user_settings"

    user_id = Column(Integer, primary_key=True, index=True)
    background = Column(String(255), nullable=True)
