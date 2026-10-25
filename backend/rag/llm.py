"""DeepSeek chat transport shared by answer, rewrite and intent calls."""

import logging
import os
import time
from typing import Optional

import httpx

logger = logging.getLogger(__name__)

ANSWER_MODEL = "deepseek-flash"
_ANSWER_URL = "https://api.deepseek.com/chat/completions"
_ANSWER_TIMEOUT = 60.0
_ANSWER_ATTEMPTS = 2


class AnswerUnavailable(RuntimeError):
    """The answer could not be produced (no key, or the model call failed)."""



def deepseek_chat(
    prompt: str,
    *,
    system: str,
    max_tokens: int = 512,
    temperature: float = 0.3,
) -> Optional[str]:
    """One DeepSeek Flash chat completion; None on missing key or failure."""
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        return None

    payload = {
        "model": ANSWER_MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        "thinking": {"type": "disabled"},
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": False,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    for attempt in range(_ANSWER_ATTEMPTS):
        try:
            response = httpx.post(
                _ANSWER_URL,
                json=payload,
                headers=headers,
                timeout=_ANSWER_TIMEOUT,
            )
            if response.status_code == 429 or response.status_code >= 500:
                raise RuntimeError(f"HTTP {response.status_code}")
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return (content or "").strip()
        except Exception as exc:
            logger.warning(
                "DeepSeek answer failed (attempt %s/%s): %s",
                attempt + 1,
                _ANSWER_ATTEMPTS,
                exc,
            )
            if attempt == 0:
                time.sleep(2)

    return None


