"""Per-source throttling and cooldown for external metadata providers."""

from __future__ import annotations

import asyncio
import os
import time

DEFAULT_INTERVAL = float(os.environ.get("METADATA_MIN_INTERVAL", "2.5"))
DEFAULT_COOLDOWN = 600.0

_locks: dict[str, asyncio.Lock] = {}
_last: dict[str, float] = {}
_cooldown: dict[str, float] = {}


def interval_for(source: str) -> float:
    env = os.environ.get(f"METADATA_{source.upper()}_INTERVAL")
    return float(env) if env is not None else DEFAULT_INTERVAL


async def throttle(source: str) -> None:
    lock = _locks.setdefault(source, asyncio.Lock())
    async with lock:
        wait = _last.get(source, 0.0) + interval_for(source) - time.monotonic()
        if wait > 0:
            await asyncio.sleep(wait)
        _last[source] = time.monotonic()


def set_cooldown(source: str, seconds: float = DEFAULT_COOLDOWN) -> None:
    _cooldown[source] = time.monotonic() + seconds


def in_cooldown(source: str) -> bool:
    until = _cooldown.get(source)
    return bool(until and until > time.monotonic())


def reset() -> None:
    _locks.clear()
    _last.clear()
    _cooldown.clear()
