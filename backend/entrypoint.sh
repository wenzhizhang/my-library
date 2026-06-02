#!/bin/sh
# Backend entrypoint: seed database if not present or seed is newer, then start uvicorn.
set -e

SEED_DB="/app/seed/demo.db"
DATA_DB="/app/data/demo.db"

if [ -f "${SEED_DB}" ]; then
    if [ ! -f "${DATA_DB}" ]; then
        echo "[entrypoint] No database found — copying seed ${SEED_DB} → ${DATA_DB}"
        cp "${SEED_DB}" "${DATA_DB}"
    elif [ "${SEED_DB}" -nt "${DATA_DB}" ]; then
        echo "[entrypoint] Seed is newer — updating database ${SEED_DB} → ${DATA_DB}"
        cp "${SEED_DB}" "${DATA_DB}"
    else
        echo "[entrypoint] Existing database is up to date — skipping seed"
    fi
fi

echo "[entrypoint] Starting uvicorn..."
exec uvicorn main:app --host 0.0.0.0 --port 8000 --proxy-headers
