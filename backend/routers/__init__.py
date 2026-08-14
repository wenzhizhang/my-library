from .author import router as author_router
from .book import router as book_router
from .bookshelf import router as bookshelf_router
from .category import router as category_router
from .publisher import publisher_router, brand_router
from .book_collection import router as book_collection_router
from .reading_plan import router as reading_plan_router
from .series import router as series_router
from .application import router as application_router
from .user import router as user_router
from .rag import router as rag_router
from .isbn import router as isbn_router
from .stats import router as stats_router
from .config_router import router as config_router
from .background import router as background_router
from .export import router as export_router
from .media import router as media_router

__all__ = [
    "author_router",
    "book_router",
    "bookshelf_router",
    "category_router",
    "publisher_router",
    "brand_router",
    "series_router",
    "application_router",
    "user_router",
    "rag_router",
    "book_collection_router",
    "reading_plan_router",
    "stats_router",
    "config_router",
    "background_router",
    "isbn_router",
    "export_router",
    "media_router",
]
