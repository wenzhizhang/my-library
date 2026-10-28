from pydantic import BaseModel, Field

RAG_HISTORY_MAX_MESSAGES = 40
RAG_TURN_CONTENT_MAX = 20000
RAG_TURN_CONTENT_LIMIT = 4000
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
    """One earlier turn of the conversation.

    ``content`` may be blank: a "more books" reply carries its payload in
    ``more`` rather than prose, and older clients sent such turns with an
    empty string. The backend drops blank turns before using them.
    """
    role: Literal["user", "assistant"]
    content: str = Field("", max_length=RAG_TURN_CONTENT_MAX)


class PageContext(BaseModel):
    """The detail page the user is looking at when they ask a question."""
    kind: Literal["book", "author", "publisher", "series", "brand"]
    id: int = Field(..., ge=1)


class RagAnswerRequest(BaseModel):
    """Answer generation request."""
    query: str = Field(..., min_length=1, description="Natural-language question")
    top_k: int = Field(5, ge=1, le=5, description="Max candidate books")
    history: List[RagTurn] = Field(
        default_factory=list,
        max_length=RAG_HISTORY_MAX_MESSAGES,
        description="Earlier turns (up to 40 messages); follow-ups are rewritten against them",
    )
    seen_book_ids: List[int] = Field(
        default_factory=list,
        max_length=2000,
        description="Books already shown in the conversation; used for 'more'",
    )
    focus_book_id: Optional[int] = Field(
        None,
        ge=1,
        description="Answer from this book only (the user quoted it)",
    )
    page_context: Optional[PageContext] = Field(
        None,
        description="Detail page context (book/author/publisher/series/brand)",
    )


class RagAnswerSource(BaseModel):
    """One book an answer was grounded in."""
    book_id: int
    title: Optional[str] = None
    title_cn: Optional[str] = None
    authors: List[str] = []
    thumb_image: Optional[str] = None


class RagAnswerResponse(BaseModel):
    """Grounded answer, or ``no_answer`` when the library has nothing."""
    query: str
    answer: str
    sources: List[RagAnswerSource] = []
    no_answer: bool = False
    rewritten_query: str = ""
    more: List[RagAnswerSource] = []
    has_more: bool = False
    reason: str = ""
    scope: Optional[Literal["book", "author"]] = None
    stats: Optional[dict] = None


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

