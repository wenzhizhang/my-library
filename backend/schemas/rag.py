from pydantic import BaseModel, Field
from typing import List, Optional


class RagSearchRequest(BaseModel):
    """Hybrid search request payload."""
    query: str = Field(..., min_length=1, description="Natural-language search query")
    top_k: int = Field(10, ge=1, le=100, description="Number of results")
    alpha: float = Field(0.5, ge=0.0, le=1.0, description="Vector weight (0=FTS5, 1=pure vector)")


class RagSearchResultItem(BaseModel):
    """A single search result."""
    book_id: int
    score: float
    title: Optional[str] = None
    title_cn: Optional[str] = None
    authors: List[str] = []


class RagSearchResponse(BaseModel):
    """Hybrid search response."""
    query: str
    total: int
    results: List[RagSearchResultItem]


class RagReindexResponse(BaseModel):
    """Reindex operation trigger result."""
    status: str = "idle"  # idle | indexing | completed | error
    message: str = ""


class RagStatusResponse(BaseModel):
    """RAG index status with reindex progress."""
    indexed_count: int
    total_books: int
    model_loaded: bool
    reindex_status: str = "idle"       # idle | indexing | completed | error
    reindex_indexed: int = 0
    reindex_failed: int = 0
    reindex_total: int = 0
    reindex_detail: str = ""

