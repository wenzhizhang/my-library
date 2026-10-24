"""RAG module for semantic book search.

Provides embedding generation (via fastembed/BGE) and vector storage
(sqlite-vec) integrated into the existing per-user SQLite databases.
"""

from .embedding import get_embedding_model, embed_texts, embed_text, is_model_loaded
from .vector_store import (
    ensure_rag_tables,
    delete_book_vector,
    prune_missing_books,
    upsert_book_vector,
    upsert_book_fts,
    upsert_book_state,
    delete_book_state,
    record_embedding_fingerprint,
    search_vectors,
    search_fts,
    hybrid_search,
)
from .document import (
    build_book_document,
    build_book_embedding_document,
    build_book_fts_fields,
)

__all__ = [
    "get_embedding_model",
    "embed_texts",
    "embed_text",
    "is_model_loaded",
    "ensure_rag_tables",
    "delete_book_vector",
    "prune_missing_books",
    "upsert_book_vector",
    "upsert_book_fts",
    "upsert_book_state",
    "delete_book_state",
    "record_embedding_fingerprint",
    "search_vectors",
    "search_fts",
    "hybrid_search",
    "build_book_document",
    "build_book_embedding_document",
    "build_book_fts_fields",
]
