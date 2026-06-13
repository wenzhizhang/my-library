#!/usr/bin/env python3
"""
Sync BookCollections from local wenzhi account to https://dingfengbo.top wenzhi account.

Flow:
  1. Read all local BookCollections from SQLite (name, intro, book IDs).
  2. For each: create collection on remote with name + intro → get remote ID.
  3. Batch-add the local book IDs to the remote collection.

Environment:
  MY_LIBRARY_PASSWORD   password for wenzhi on the remote server (required)
"""

import os
import sys
import json
import sqlite3
import subprocess
import urllib.request
import urllib.error
import ssl
from typing import Optional
REMOTE_BASE = "https://dingfengbo.top"
USERNAME = "wenzhi"
REMOTE_PASSWORD = os.environ.get("MY_LIBRARY_PASSWORD")
LOCAL_BASE = os.environ.get("LOCAL_BASE_URL", "http://127.0.0.1:8000")
LOCAL_PASSWORD = os.environ.get("LOCAL_PASSWORD")

DATA_DIR = os.environ.get("DATA_DIR", "/app/data")
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

_AUTH_DB_CANDIDATES = [
    os.path.join(DATA_DIR, "auth.db"),
    os.path.join(_SCRIPT_DIR, "backend", "auth.db"),
    os.path.join(_SCRIPT_DIR, "auth.db"),
]

_DEMO_DB_CANDIDATES = [
    os.path.join(_SCRIPT_DIR, "backend", "data", "demo.db"),
    os.path.join(DATA_DIR, "demo.db"),
    os.path.join(_SCRIPT_DIR, "demo.db"),
]

# ═══════════════════════════════════════════════════════════════════
# HTTP helpers
# ═══════════════════════════════════════════════════════════════════

def _request(method: str, url: str, body: Optional[dict] = None, token: Optional[str] = None) -> tuple[int, dict]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")

    ctx = ssl.create_default_context()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, context=ctx) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8") if e.fp else ""
        try:
            body_resp = json.loads(raw) if raw else {"detail": str(e)}
        except json.JSONDecodeError:
            body_resp = {"detail": f"HTTP {e.code}: {raw[:200]}"}
def login(base_url: str, password: str) -> str:
    status, resp = _request("POST", f"{base_url}/api/auth/login", {
        "username": USERNAME,
        "password": password,
    })
    if status != 200:
        sys.exit(f"Login failed at {base_url}: {resp.get('detail', resp)}")
    return resp["access_token"]


def create_collection(token: str, name: str, intro: Optional[str]) -> dict:
    body = {"name": name}
    if intro:
        body["intro"] = intro
    status, resp = _request("POST", f"{REMOTE_BASE}/api/book-collections/", body, token=token)
    if status != 200:
        sys.exit(f"Failed to create collection '{name}': {resp.get('detail', resp)}")
    return resp


def batch_add_books(token: str, collection_id: int, book_ids: list[int]):
    status, resp = _request("POST",
        f"{REMOTE_BASE}/api/book-collections/{collection_id}/books/batch",
        {"book_ids": book_ids}, token=token)
    if status != 200:
        print(f"  [WARN] Batch add failed: {resp.get('detail', resp)}", file=sys.stderr)


# ═══════════════════════════════════════════════════════════════════
# Local SQLite
# ═══════════════════════════════════════════════════════════════════

def _find_auth_db() -> Optional[str]:
    for path in _AUTH_DB_CANDIDATES:
        if os.path.isfile(path):
            return path
    return None


def _find_user_uuid(auth_db_path: str, username: str) -> Optional[str]:
    conn = sqlite3.connect(f"file:{auth_db_path}?mode=ro", uri=True)
    try:
        row = conn.execute("SELECT uuid FROM users WHERE username = ?", (username,)).fetchone()
        return row[0] if row else None
    finally:
        conn.close()


def _find_user_db(auth_db_path: str, username: str) -> Optional[str]:
    uuid = _find_user_uuid(auth_db_path, username)
    if not uuid:
        return None
    auth_dir = os.path.dirname(auth_db_path)
    user_db = os.path.join(auth_dir, f"{uuid}.db")
    return user_db if os.path.isfile(user_db) else None


def read_local_collections(db_path: str) -> list[dict]:
    """
    Returns list of {name, intro, book_ids: [int, ...]}.
    """
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        # Check if the table exists (migration may not have been run)
        cur = conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='book_collections'"
        )
        if not cur.fetchone():
            sys.exit(
                f"Table 'book_collections' not found in {db_path}.\n"
                "Run: cd backend && alembic upgrade head"
            )

        collections = conn.execute(
            "SELECT id, name, intro FROM book_collections ORDER BY name"
        ).fetchall()

        result = []
        for col in collections:
            rows = conn.execute(
                "SELECT book_id FROM book_collection_items WHERE collection_id = ?",
                (col[0],)
            ).fetchall()
            book_ids = [r[0] for r in rows]

            result.append({
                "name": col[1],
                "intro": col[2],
                "book_ids": book_ids,
            })
        return result
    finally:
        conn.close()


def read_local_collections_via_docker(container: str = "my-library-backend-1") -> list[dict]:
    """Read collections from the wenzhi user DB inside a Docker container."""
    script = """
import sqlite3, json, os
conn = sqlite3.connect('/app/data/auth.db')
users = conn.execute("SELECT uuid FROM users WHERE username = 'wenzhi'").fetchall()
conn.close()
if not users:
    raise SystemExit('User wenzhi not found in auth.db')
uuid = users[0][0]
db = f'/app/data/{uuid}.db'
if not os.path.isfile(db):
    raise SystemExit(f'User DB not found: {db}')
conn = sqlite3.connect(db)
cols = conn.execute("SELECT id, name, intro FROM book_collections ORDER BY name").fetchall()
result = []
for col in cols:
    rows = conn.execute("SELECT book_id FROM book_collection_items WHERE collection_id = ?", (col[0],)).fetchall()
    result.append({"name": col[1], "intro": col[2], "book_ids": [r[0] for r in rows]})
conn.close()
print(json.dumps(result))
"""
    try:
        proc = subprocess.run(
            ["docker", "exec", container, "python3", "-c", script],
            capture_output=True, text=True, timeout=15,
        )
        if proc.returncode != 0:
            sys.exit(f"Docker exec failed: {proc.stderr.strip()}")
        return json.loads(proc.stdout.strip())
    except FileNotFoundError:
        sys.exit("Docker not found. Is it installed?")
    except json.JSONDecodeError:
        sys.exit(f"Failed to parse Docker output: {proc.stdout[:200]}")


# ═══════════════════════════════════════════════════════════════════
# Main
def _find_demo_db() -> Optional[str]:
    for path in _DEMO_DB_CANDIDATES:
        if os.path.isfile(path):
            return path
    return None


def sync(dry_run: bool = False, local_db: Optional[str] = None,
         use_docker: bool = False, use_local_api: bool = False):
    # ── 1. Find local DB ──
    if use_docker:
        print("Local: reading via docker exec (wenzhi user DB)")
        local_collections = read_local_collections_via_docker()
    elif use_local_api:
        local_token: Optional[str] = None
        if LOCAL_PASSWORD:
            local_token = login(LOCAL_BASE, LOCAL_PASSWORD)
            print(f"Local: authenticated as {USERNAME} via API")
        else:
            print("Local: no auth (set LOCAL_PASSWORD if your local instance requires it)")
        status, resp = _request("GET",
            f"{LOCAL_BASE}/api/book-collections/?page=1&limit=100", token=local_token)
        if status != 200:
            sys.exit(f"Failed to list local collections: {resp}")
        summaries = resp.get("book_collections", [])
        local_collections = []
        for s in summaries:
            status2, detail = _request("GET",
                f"{LOCAL_BASE}/api/book-collections/{s['id']}", token=local_token)
            if status2 != 200:
                sys.exit(f"Failed to get local collection {s['id']}: {detail}")
            book_ids = [b["id"] for b in (detail.get("books") or [])]
            local_collections.append({
                "name": detail["name"],
                "intro": detail.get("intro"),
                "book_ids": book_ids,
            })
    else:
        if local_db:
            if not os.path.isfile(local_db):
                sys.exit(f"Local DB not found: {local_db}")
            user_db = local_db
            print(f"Local: reading {user_db}")
        else:
            user_db = None
            auth_db = _find_auth_db()
            if auth_db:
                user_db = _find_user_db(auth_db, USERNAME)
                if user_db:
                    print(f"Local: reading {user_db} (found via {auth_db})")
            if not user_db:
                user_db = _find_demo_db()
                if user_db:
                    print(f"Local: reading demo.db ({user_db})")
            if not user_db:
                sys.exit(
                    "Cannot find local database.\n"
                    "  Tried: auth.db -> user DB, demo.db in backend/data/, /app/data/, .\n"
                    "  Use --docker if running in Docker, --local-api for API, or --local-db PATH."
                )
        local_collections = read_local_collections(user_db)

    if not local_collections:
        sys.exit("No local BookCollections found.")

    # ── 2. Remote auth ──
    token = login(REMOTE_BASE, REMOTE_PASSWORD)
    print(f"\nRemote: authenticated as {USERNAME}")

    # ── 3. Sync each collection ──
    stats = {"created": 0, "books_added": 0}

    for c in local_collections:
        name = c["name"]
        intro = c.get("intro")
        book_ids = c["book_ids"]
        print(f"\n── {name} ({len(book_ids)} books) ──")

        if dry_run:
            print(f"  [DRY-RUN] Would create collection '{name}'")
            if book_ids:
                print(f"  [DRY-RUN] Would add {len(book_ids)} books: {book_ids}")
            continue

        # Create collection on remote
        created = create_collection(token, name, intro)
        remote_id = created["id"]
        print(f"  Created remote collection (id={remote_id})")
        stats["created"] += 1

        # Batch-add books
        if book_ids:
            batch_add_books(token, remote_id, book_ids)
            print(f"  Added {len(book_ids)} books")
            stats["books_added"] += len(book_ids)

    # ── Summary ──
    print(f"\n{'='*50}")
    if dry_run:
        print("DRY RUN — no changes made")
    print(f"Collections created: {stats['created']}")
    print(f"Books added:         {stats['books_added']}")
    print("Done.")


if __name__ == "__main__":
    if not REMOTE_PASSWORD:
        sys.exit("MY_LIBRARY_PASSWORD environment variable is required")

    dry_run = "--dry-run" in sys.argv
    use_docker = "--docker" in sys.argv
    use_local_api = "--local-api" in sys.argv

    local_db = None
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--local-db" and i + 1 < len(args):
            i += 1
            local_db = args[i]
        i += 1

    sync(dry_run=dry_run, local_db=local_db,
         use_docker=use_docker, use_local_api=use_local_api)
