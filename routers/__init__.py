from .author import router as author_router
from .book import router as book_router
from .bookshelf import router as bookshelf_router
from .category import router as category_router
from .publisher import publisher_router, brand_router
from .series import router as series_router

__all__ = [
    "author_router",
    "book_router",
    "bookshelf_router",
    "category_router",
    "publisher_router",
    "brand_router",
    "series_router"
]
