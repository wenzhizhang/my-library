"""Config values served from JSON files for hot-reload (no rebuild needed)."""

import json
import os

from fastapi import APIRouter

router = APIRouter(prefix="/api/config", tags=["config"])

CONFIG_DIR = os.path.join(os.path.dirname(__file__), "..", "config")


def _load_json(filename: str) -> dict:
    path = os.path.join(CONFIG_DIR, filename)
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


@router.get("/purchase-stores")
def get_purchase_stores():
    """Return purchase store options — reloaded from file on every request."""
    data = _load_json("purchase_stores.json")
    return {"purchase_stores": data.get("purchase_stores", [])}
