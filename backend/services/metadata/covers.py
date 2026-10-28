"""Download external covers into the local media store.

Douban's image CDN returns 418 without a ``Referer`` header, so the downloader
always sends one for Douban URLs.  The stored path matches what the frontend
already requests through ``/api/media``.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path

import httpx

USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
MAX_BYTES = 8 * 1024 * 1024
_EXTENSIONS = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
}


def _media_root() -> Path:
    explicit = os.environ.get("MEDIA_LOCAL_DIR")
    if explicit:
        return Path(explicit)
    return Path(os.environ.get("DATA_DIR", "/app/data")) / "media"


def _cover_dir() -> Path:
    path = _media_root() / "images" / "books"
    path.mkdir(parents=True, exist_ok=True)
    return path


def _existing(name: str) -> str:
    for path in _cover_dir().glob(f"{name}.*"):
        return f"/images/books/{path.name}"
    return ""


async def download(url: str, referer: str = "") -> str:
    if not url:
        return ""
    if url.startswith("/images/"):
        return url
    name = hashlib.sha1(url.encode("utf-8")).hexdigest()[:24]
    existing = _existing(name)
    if existing:
        return existing

    headers = {"User-Agent": USER_AGENT}
    if referer:
        headers["Referer"] = referer
    try:
        async with httpx.AsyncClient(follow_redirects=True, timeout=12) as client:
            response = await client.get(url, headers=headers)
    except httpx.HTTPError:
        return ""
    if response.status_code != 200:
        return ""
    content_type = (response.headers.get("content-type") or "").split(";")[0].strip()
    extension = _EXTENSIONS.get(content_type)
    if extension is None:
        return ""
    content = response.content
    if not content or len(content) > MAX_BYTES:
        return ""
    filename = f"{name}{extension}"
    try:
        (_cover_dir() / filename).write_bytes(content)
    except OSError:
        return ""
    return f"/images/books/{filename}"
