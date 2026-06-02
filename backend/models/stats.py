from sqlalchemy import Column, Integer, String, DateTime
from datetime import datetime

from .base import Base


class VisitLog(Base):
    __tablename__ = "visit_logs"

    id = Column(Integer, primary_key=True, index=True)
    ip = Column(String(45), nullable=False)
    path = Column(String(500), nullable=True)
    timestamp = Column(DateTime, default=datetime.now, index=True)
