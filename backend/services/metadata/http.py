"""Shared HTTP fetch for metadata providers: throttle, cooldown and errors."""

from __future__ import annotations

from typing import Optional

import httpx

from .rate_limit import in_cooldown, set_cooldown, throttle

TIMEOUT = 10.0
BLOCKED_STATUSES = (403, 418, 429)


async def fetch(
    source: str,
    url: str,
    *,
    headers: dict[str, str],
    timeout: float = TIMEOUT,
) -> Optional[httpx.Response]:
    if in_cooldown(source):
        return None
    await throttle(source)
    try:
        async with httpx.AsyncClient(follow_redirects=True, timeout=timeout) as client:
            response = await client.get(url, headers=headers)
    except httpx.HTTPError:
        return None
    if response.status_code in BLOCKED_STATUSES:
        set_cooldown(source)
        return None
    if response.status_code != 200:
        return None
    return response
