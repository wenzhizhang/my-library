"""Media proxy — fetches from CDN, optionally caches locally.

Set MEDIA_CACHE_ENABLED=true to enable local disk caching.
When disabled (default), every request proxies to CDN directly.
"""

import os
import logging
import time
from pathlib import Path

import httpx
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, Response

logger = logging.getLogger("media-cache")
logger.setLevel(logging.INFO)
_h = logging.StreamHandler()
_h.setFormatter(logging.Formatter("%(asctime)s [%(name)s] %(message)s"))
logger.addHandler(_h)
logger.propagate = False

router = APIRouter(prefix="/api/media", tags=["media"])

CDN_BASE = "https://cdn.dingfengbo.top/media"
FETCH_TIMEOUT = 15.0

CACHE_ENABLED = os.environ.get("MEDIA_CACHE_ENABLED", "false").lower() == "true"
CACHE_DIR = Path(__file__).resolve().parent.parent / "data" / "media-cache"

if CACHE_ENABLED:
    logger.info("Media cache ENABLED — directory: %s", CACHE_DIR)
else:
    logger.info("Media cache DISABLED — proxying all requests to CDN")


@router.get("/{path:path}")
async def serve_media(path: str):
    """Serve a media file from CDN, optionally caching locally."""
    if ".." in path:
        raise HTTPException(status_code=400, detail="Invalid path")
    path = path.lstrip("/")

    # ---- cached path ----
    if CACHE_ENABLED:
        return await _serve_cached(path)

    # ---- uncached path (default) ----
    return await _serve_proxy(path)


async def _serve_cached(path: str):
    """Serve from local cache if available, otherwise fetch + cache."""
    cache_path = CACHE_DIR / path

    if cache_path.is_file():
        if cache_path.stat().st_size == 0:
            logger.warning("[CACHE STALE] %s is 0 bytes, removing and re-fetching", path)
            cache_path.unlink(missing_ok=True)
        else:
            t0 = time.time()
            logger.info("[CACHE HIT]  %s (%.0f KB)", path, cache_path.stat().st_size / 1024)
            resp = FileResponse(cache_path)
            logger.info("[CACHE HIT]  %s served in %.0f ms", path, (time.time() - t0) * 1000)
            return resp

    # Cache miss
    cdn_url = f"{CDN_BASE}/{path}"
    logger.info("[CACHE MISS] %s → fetching from CDN", path)

    content, content_type = await _fetch(cdn_url, path)

    try:
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_bytes(content)
        logger.info("[CACHE WRITE] %s saved to cache (%.0f KB)", path, len(content) / 1024)
    except OSError as exc:
        logger.error("[CACHE WRITE] %s FAILED: %s", path, exc)

    return Response(content=content, media_type=content_type)


async def _serve_proxy(path: str):
    """Proxy directly to CDN — no local caching."""
    cdn_url = f"{CDN_BASE}/{path}"
    logger.info("[PROXY] %s → CDN", path)

    content, content_type = await _fetch(cdn_url, path)

    resp = Response(content=content, media_type=content_type)
    resp.headers["Cache-Control"] = "no-cache"
    return resp

async def _fetch(url: str, path: str):
    """Fetch from CDN, return (content, content_type)."""
    t0 = time.time()
    try:
        async with httpx.AsyncClient(timeout=FETCH_TIMEOUT, follow_redirects=True) as client:
            resp = await client.get(url)
            if resp.status_code != 200:
                raise HTTPException(status_code=404, detail="Image not found on CDN")

            content_type = resp.headers.get("content-type", "image/jpeg")
            elapsed = (time.time() - t0) * 1000
            logger.info("[FETCH] %s ← CDN in %.0f ms (%.0f KB)", path, elapsed, len(resp.content) / 1024)
            return resp.content, content_type
    except httpx.HTTPError as exc:
        logger.error("[FETCH] %s CDN FAILED: %s", path, exc)
        raise HTTPException(status_code=502, detail="Failed to fetch image from CDN")
