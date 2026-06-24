from pydantic import BaseModel, ConfigDict, field_validator, model_validator
from typing import Optional, List
from datetime import datetime, date


class ReadingPlanBookSimple(BaseModel):
    """Book in a reading plan — includes read_state + book_count for progress calc."""
    id: int
    title: str
    title_cn: Optional[str] = None
    thumb_image: Optional[str] = None
    isbn: Optional[str] = None
    authors: Optional[List[str]] = None
    read_state: Optional[str] = None
    book_count: Optional[int] = None

    model_config = ConfigDict(from_attributes=True)

    @field_validator("authors", mode="before")
    @classmethod
    def extract_author_names(cls, v):
        if v is None:
            return None
        return [str(a) for a in v]


class ReadingPlanCreation(BaseModel):
    name: str
    intro: Optional[str] = None
    start_date: Optional[date] = None
    end_date: Optional[date] = None


class ReadingPlanUpdate(BaseModel):
    name: Optional[str] = None
    intro: Optional[str] = None
    start_date: Optional[date] = None
    end_date: Optional[date] = None


class BatchAddBooks(BaseModel):
    book_ids: list[int]


class AddBookToPlan(BaseModel):
    book_id: int


class ReadingPlanResponse(BaseModel):
    id: int
    name: str
    intro: Optional[str] = None
    start_date: Optional[date] = None
    end_date: Optional[date] = None
    created_at: Optional[datetime] = None
    total_books: Optional[int] = None
    progress: Optional[float] = None  # 0–100 percentage
    books: Optional[List[ReadingPlanBookSimple]] = None

    model_config = ConfigDict(from_attributes=True)

    @model_validator(mode="after")
    def compute_derived(self):
        if self.books is not None:
            self.total_books = len(self.books)
            total_volumes = sum(b.book_count or 1 for b in self.books)
            read_volumes = sum(
                b.book_count or 1 for b in self.books if b.read_state == "read"
            )
            self.progress = round(read_volumes / total_volumes * 100, 1) if total_volumes > 0 else 0.0
        return self


class ReadingPlanSummary(BaseModel):
    id: int
    name: str
    intro: Optional[str] = None
    start_date: Optional[date] = None
    end_date: Optional[date] = None
    created_at: Optional[str] = None
    total_books: int = 0
    progress: Optional[float] = None

    model_config = ConfigDict(from_attributes=True)


class ReadingPlanListResponse(BaseModel):
    reading_plans: List[ReadingPlanSummary]
    total_pages: int
    total_plans: int
