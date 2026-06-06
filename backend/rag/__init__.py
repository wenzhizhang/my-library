"""RAG module for semantic book search.

Provides embedding generation (via fastembed/BGE) and vector storage
(sqlite-vec) integrated into the existing per-user SQLite databases.
"""

from .embedding import get_embedding_model, embed_texts, embed_text
from .vector_store import (
    ensure_rag_tables,
    delete_book_vector,
    upsert_book_vector,
    upsert_book_fts,
    search_vectors,
    search_fts,
    hybrid_search,
)
from .document import build_book_document, build_book_fts_fields

__all__ = [
    "get_embedding_model",
    "embed_texts",
    "embed_text",
    "ensure_rag_tables",
    "delete_book_vector",
    "upsert_book_vector",
    "upsert_book_fts",
    "search_vectors",
    "search_fts",
    "hybrid_search",
    "build_book_document",
    "build_book_fts_fields",
]
