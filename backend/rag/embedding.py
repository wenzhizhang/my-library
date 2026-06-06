"""Embedding model wrapper — lazy-loaded BGE via fastembed (ONNX, no torch).

Model: BAAI/bge-small-zh-v1.5  (33 MB, 512-dim via ONNX, bilingual Chinese/English)
"""

from typing import List, Optional, Sequence

_MODEL = None
_MODEL_NAME = "BAAI/bge-small-zh-v1.5"


def get_embedding_model(force_reload: bool = False):
    """Return the singleton embedding model, loading on first call.

    The model is a fastembed `TextEmbedding` instance backed by ONNX.
    Subsequent calls return the cached instance (≈zero cost).
    """
    global _MODEL
    if _MODEL is None or force_reload:
        from fastembed import TextEmbedding

        _MODEL = TextEmbedding(model_name=_MODEL_NAME)
    return _MODEL


def embed_texts(texts: Sequence[str], batch_size: int = 32) -> List[List[float]]:
    """Embed a batch of texts into 512-dim float vectors.

    Args:
        texts: List of document strings to embed.
        batch_size: ONNX batch size (default 32).

    Returns:
        List of vectors, each a list of 512 floats.
    """
    model = get_embedding_model()
    results: list[list[float]] = []
    for vec in model.embed(texts, batch_size=batch_size):
        # fastembed yields numpy arrays per text
        results.append(vec.tolist())
    return results


def embed_text(text: str) -> List[float]:
    """Embed a single text string."""
    results = embed_texts([text])
    return results[0]


def is_model_loaded() -> bool:
    """Check whether the embedding model has been loaded into memory."""
    return _MODEL is not None
