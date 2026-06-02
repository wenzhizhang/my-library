# Backend image for FastAPI app
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Seed database — shipped in the image, used on first start
COPY data/demo.db /app/seed/demo.db

# Make entrypoint executable
RUN chmod +x /app/entrypoint.sh

EXPOSE 8000
ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["/app/entrypoint.sh"]
