"""Caching reverse proxy for remote media files (Tencent COS).

First access: fetch from COS -> cache locally -> serve.
Subsequent accesses: serve directly from local cache.
"""

import logging
from pathlib import Path

import httpx
import time
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, Response

logger = logging.getLogger("media-cache")
logger.setLevel(logging.INFO)
_h = logging.StreamHandler()
_h.setFormatter(logging.Formatter("%(asctime)s [%(name)s] %(message)s"))
logger.addHandler(_h)
logger.propagate = False


router = APIRouter(prefix="/api/media", tags=["media"])
CACHE_DIR = Path(__file__).resolve().parent.parent / "data" / "media-cache"

COS_BASE = "https://zhangwenzhi-1315027057.cos.ap-guangzhou.myqcloud.com/media"
FETCH_TIMEOUT = 15.0

@router.get("/{path:path}")
async def serve_media(path: str):
    """Serve a media file, caching it locally from COS on first access."""
    if ".." in path:
        raise HTTPException(status_code=400, detail="Invalid path")
    path = path.lstrip("/")

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

    # Cache miss — fetch from COS, then cache locally
    cos_url = f"{COS_BASE}/{path}"
    logger.info("[CACHE MISS] %s → fetching from COS", path)

    try:
        t0 = time.time()
        async with httpx.AsyncClient(timeout=FETCH_TIMEOUT, follow_redirects=True) as client:
            resp = await client.get(cos_url)
            if resp.status_code != 200:
                raise HTTPException(status_code=404, detail="Image not found on remote")

            content_type = resp.headers.get("content-type", "image/jpeg")
            elapsed = (time.time() - t0) * 1000
            logger.info("[CACHE MISS] %s fetched from COS in %.0f ms (%.0f KB)",
                        path, elapsed, len(resp.content) / 1024)

            # Write to local cache (best-effort, don't fail the request)
            try:
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                cache_path.write_bytes(resp.content)
                logger.info("[CACHE WRITE] %s saved to cache (%.0f KB)", path, len(resp.content) / 1024)
            except OSError as exc:
                logger.error("[CACHE WRITE] %s FAILED: %s", path, exc)

            result = Response(content=resp.content, media_type=content_type)
            result.headers["X-Cache"] = "MISS"
            result.headers["Cache-Control"] = "public, max-age=86400"
            return result
    except httpx.HTTPError as exc:
        logger.error("[CACHE MISS] %s COS fetch FAILED: %s", path, exc)
        raise HTTPException(status_code=502, detail="Failed to fetch image from remote")
