"""Pre-download and cache the BGE embedding model during Docker build."""
import os

# Respect build-time HF_ENDPOINT override
endpoint = os.environ.get("HF_ENDPOINT", "https://huggingface.co")
os.environ["HF_ENDPOINT"] = endpoint

from fastembed import TextEmbedding

model = TextEmbedding(model_name="BAAI/bge-small-zh-v1.5")
# warm up — run one inference so ONNX session is fully initialized
list(model.embed(["warmup query"]))
print(f"[cache_model] BGE model cached ({endpoint})")
