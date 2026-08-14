"""Background image list (config-driven) + per-user background selection.

The available backgrounds are re-read from ``config/backgrounds.json`` on
every request, and ``backend/config`` is bind-mounted into the container
read-only, so the list can change without rebuilding the image/container.
The per-user selection lives in the auth database (user_settings table):
each user's choice is isolated from other users and from guests (guests
always fall back to the configured default).
"""

import json
import logging
import os
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from auth import get_current_user_id, require_user_id
from database import get_auth_db
from models.user import UserSetting
from schemas.background import (
    BackgroundItem,
    BackgroundListResponse,
    BackgroundSelectionRequest,
    BackgroundSelectionResponse,
)

router = APIRouter(prefix="/api/backgrounds", tags=["backgrounds"])

logger = logging.getLogger(__name__)

CONFIG_DIR = os.path.join(os.path.dirname(__file__), "..", "config")

# Served only when config/backgrounds.json is missing or unreadable — e.g. a
# host checkout that hasn't been synced yet — so the feature degrades to the
# historic default instead of 500ing on every request. Once the real file is
# in place it is hot-reloaded and this fallback is never used again.
_FALLBACK_BACKGROUNDS: dict = {
    "default": "bg4",
    "backgrounds": [
        {
            "id": "bg4",
            "name": "背景 4",
            "url": "https://cdn.dingfengbo.top/media/background/bg4.jpg",
        }
    ],
}

# Last successfully loaded config: served if the file becomes temporarily
# unreadable (e.g. a non-atomic edit), so a transient glitch doesn't swap the
# whole list to the fallback.
_loaded_config: Optional[dict] = None


def _load_background_config() -> dict:
    global _loaded_config
    path = os.path.join(CONFIG_DIR, "backgrounds.json")
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as exc:
        if _loaded_config is not None:
            logger.warning(
                "backgrounds config %s unreadable (%s); serving last-known-good", path, exc
            )
            return _loaded_config
        logger.warning(
            "backgrounds config %s unavailable (%s); serving built-in fallback", path, exc
        )
        return _FALLBACK_BACKGROUNDS
    _loaded_config = data
    return data


@router.get("", response_model=BackgroundListResponse)
def list_backgrounds():
    """Available background images, hot-reloaded from the config file."""
    data = _load_background_config()
    backgrounds = [
        BackgroundItem(id=b["id"], name=b.get("name", b["id"]), url=b["url"])
        for b in data.get("backgrounds", [])
    ]
    return BackgroundListResponse(
        default_id=data.get("default", ""), backgrounds=backgrounds
    )


@router.get("/me", response_model=BackgroundSelectionResponse)
def get_my_background(
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_auth_db),
):
    """The caller's saved background id.

    Guests (no/invalid token) always get ``null`` and keep the default.
    """
    if user_id is None:
        return BackgroundSelectionResponse(background_id=None)
    setting = (
        db.query(UserSetting).filter(UserSetting.user_id == user_id).first()
    )
    return BackgroundSelectionResponse(
        background_id=setting.background if setting else None
    )


@router.put("/me", response_model=BackgroundSelectionResponse)
def set_my_background(
    data: BackgroundSelectionRequest,
    user_id: int = Depends(require_user_id),
    db: Session = Depends(get_auth_db),
):
    """Save the caller's background choice (must be one of the configured ids)."""
    background_id = data.background_id.strip()
    known = {
        b["id"] for b in _load_background_config().get("backgrounds", [])
    }
    if not background_id or background_id not in known:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown background id: {data.background_id!r}",
        )

    setting = (
        db.query(UserSetting).filter(UserSetting.user_id == user_id).first()
    )
    if setting is None:
        setting = UserSetting(user_id=user_id, background=background_id)
        db.add(setting)
    else:
        setting.background = background_id
    try:
        db.commit()
    except IntegrityError:
        # Concurrent save for the same user inserted the row between our
        # SELECT and INSERT — re-read and update instead of 500ing.
        db.rollback()
        setting = (
            db.query(UserSetting).filter(UserSetting.user_id == user_id).first()
        )
        setting.background = background_id
        db.commit()
    return BackgroundSelectionResponse(background_id=background_id)
