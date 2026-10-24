from pydantic import BaseModel, Field
from typing import List, Literal, Optional


class RagSearchRequest(BaseModel):
    """Hybrid search request payload."""
    query: str = Field(..., min_length=1, description="Natural-language search query")
    top_k: int = Field(10, ge=1, le=100, description="Number of results")
    alpha: float = Field(0.5, ge=0.0, le=1.0, description="Vector weight (0=FTS5, 1=pure vector)")
    min_score: float = Field(
        0.0, ge=0.0, le=1.0, description="Drop results below this merged score"
    )


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


class RagSummarizeResponse(BaseModel):
    """Summary generated for one book."""
    book_id: int
    summary: str
    source: Literal["existing", "local", "deepseek"]
    updated: bool


class RagTurn(BaseModel):
    """One earlier turn of the conversation."""
    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1)


class RagAnswerRequest(BaseModel):
    """Answer generation request."""
    query: str = Field(..., min_length=1, description="Natural-language question")
    top_k: int = Field(5, ge=1, le=5, description="Max candidate books")
    history: List[RagTurn] = Field(
        default_factory=list,
        max_length=20,
        description="Earlier turns; follow-ups are rewritten against them",
    )


class RagAnswerSource(BaseModel):
    """One book an answer was grounded in."""
    book_id: int
    title: Optional[str] = None
    title_cn: Optional[str] = None
    authors: List[str] = []


class RagAnswerResponse(BaseModel):
    """Grounded answer, or ``no_answer`` when the library has nothing."""
    query: str
    answer: str
    sources: List[RagAnswerSource] = []
    no_answer: bool = False
    rewritten_query: str = ""


class RagReindexResponse(BaseModel):
    """Reindex operation trigger result."""
    status: str = "idle"  # idle | indexing | completed | error
    message: str = ""


class RagStatusResponse(BaseModel):
    """RAG index status with reindex progress."""
    indexed_count: int
    total_books: int
    model_loaded: bool
    index_model: str = ""
    index_model_matches: bool = True
    reindex_status: str = "idle"       # idle | indexing | completed | error
    reindex_indexed: int = 0
    reindex_failed: int = 0
    reindex_total: int = 0
    reindex_detail: str = ""

