"""Response serializers shared across routers.

Keeps the public book-list contract consistent everywhere: ``authors`` is a
list of display strings (``str(Author)`` → ``"[dynasty] name_cn"`` /
``"[nation] name_cn"``), matching the original ``/api/books/`` shape.
"""


def serialize_book(book) -> dict:
    """Serialize a Book ORM object into the public list shape.

    Only the fields the UI card needs; publisher/category are ``{id, name}``
    pairs. ``book.authors`` must be loaded on the instance.
    """
    return {
        "id": book.id,
        "isbn": book.isbn,
        "title_cn": book.title_cn,
        "title": book.title,
        "thumb_image": book.thumb_image,
        "authors": [str(author) for author in book.authors],
        "publisher": {"id": book.publisher.id, "name": book.publisher.name} if book.publisher else None,
        "category": {"id": book.category.id, "name": book.category.name} if book.category else None,
    }
