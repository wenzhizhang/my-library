# Backend image for FastAPI app
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Seed database — shipped in the image, used on first start
COPY data/demo.db /app/seed/demo.db

# Project config — baked into the image as fallback
COPY config/projects.json /app/config/projects.json

# Make entrypoint executable
RUN chmod +x /app/entrypoint.sh

EXPOSE 8000
ENV PYTHONUNBUFFERED=1

# Model download endpoint. Change to https://huggingface.co if outside China.
ENV HF_ENDPOINT=https://hf-mirror.com
# Model cache directory — outside the DATA volume so pre-downloaded
# model survives container restarts without being shadowed by the volume.
ENV HF_HOME=/app/.model_cache
ENTRYPOINT ["/app/entrypoint.sh"]
