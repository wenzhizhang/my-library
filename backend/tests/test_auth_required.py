"""Every write endpoint the spec marks BearerAuth answers 401 without a token.

The book, collection, plan and application write endpoints declared only `Depends(get_db)`, so an
anonymous request reached the handler: on the live service `DELETE /api/books/{id}`,
`POST /api/reading-plans` and `POST /api/book-collections` all answered 200, and `POST /api/books`
reached the database layer. The spec had always said otherwise ("write endpoints answer 401 without
a token"), so this pins the contract per operation instead of trusting one file's signature.

`tests/conftest.py` stands in for the token so the CRUD tests stay about their payloads; these tests
ask for the `guest` client, which has no token at all.

Paths are the ones the routes serve, taken from FastAPI's own route table — this backend mixes
spellings (the collection POSTs carry a trailing slash, the item routes do not), which is why the
clients carry a redirect interceptor.
"""

import pytest

# (method, path) as the router serves it; ids are dummies, the token is checked before the handler.
SECURED_WRITES = [
    ("post", "/api/books/"),
    ("put", "/api/books/1"),
    ("delete", "/api/books/1"),
    ("put", "/api/books/1/archive"),
    ("post", "/api/book-collections/"),
    ("put", "/api/book-collections/1"),
    ("delete", "/api/book-collections/1"),
    ("post", "/api/book-collections/1/books"),
    ("post", "/api/book-collections/1/books/batch"),
    ("delete", "/api/book-collections/1/books/1"),
    ("post", "/api/reading-plans/"),
    ("put", "/api/reading-plans/1"),
    ("delete", "/api/reading-plans/1"),
    ("post", "/api/reading-plans/1/books"),
    ("post", "/api/reading-plans/1/books/batch"),
    ("delete", "/api/reading-plans/1/books/1"),
    ("post", "/api/applications/"),
    ("put", "/api/applications/1"),
    ("delete", "/api/applications/1"),
]


@pytest.mark.parametrize("method,path", SECURED_WRITES, ids=[f"{m} {p}" for m, p in SECURED_WRITES])
def test_write_endpoints_require_a_token(guest, method, path):
    verb = getattr(guest, method)
    # DELETE takes no body; the others get an empty object, which proves the token is checked first.
    response = verb(path) if method == "delete" else verb(path, json={})

    assert response.status_code == 401, f"{method.upper()} {path} answered {response.status_code}"
    assert response.json()["detail"] == "Not authenticated"


def test_read_endpoints_stay_open(guest):
    """Reads deliberately fall back to the shared demo database instead of failing."""
    assert guest.get("/api/books/").status_code == 200
    assert guest.get("/api/stats/books").status_code == 200


def test_logins_own_401_is_not_the_missing_token_one(guest):
    """A guest can still reach authentication itself."""
    response = guest.post("/api/auth/login", json={"username": "nobody", "password": "nothing"})

    assert response.status_code == 401
    assert response.json()["detail"] != "Not authenticated"


def test_guests_get_a_null_background_rather_than_an_error(guest):
    """/api/backgrounds/me takes an *optional* token by design: guests keep the default picture."""
    response = guest.get("/api/backgrounds/me")

    assert response.status_code == 200
    assert response.json()["background_id"] is None
