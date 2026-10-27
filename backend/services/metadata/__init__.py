"""External book metadata: adapters, cache and merging."""

from .service import lookup_book_info, lookup_isbn_metadata, search_metadata

__all__ = ["lookup_book_info", "lookup_isbn_metadata", "search_metadata"]
