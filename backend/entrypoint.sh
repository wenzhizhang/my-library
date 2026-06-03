#!/bin/sh
# Backend entrypoint: verify deps, seed database, start uvicorn.
set -e

# Verify critical packages are importable
python3 -c "
import sys
packages = ['jose', 'passlib', 'bcrypt']
for p in packages:
    try:
        __import__(p)
        print(f'[entrypoint] {p} OK')
    except ImportError as e:
        print(f'[entrypoint] ERROR: {p} not installed — {e}')
        sys.exit(1)
" || exit 1

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

# Sync projects from config
CONFIG_FILE="/app/config/projects.json"
if [ -f "$CONFIG_FILE" ]; then
    echo "[entrypoint] Syncing projects from $CONFIG_FILE ..."
    python3 -c "
import json, sqlite3
conn = sqlite3.connect('/app/data/applications.db')
conn.execute('CREATE TABLE IF NOT EXISTS applications (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, url TEXT, icon_url TEXT, sort_order INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)')
conn.commit()
conn.close()
with open('$CONFIG_FILE') as f:
    projects = json.load(f)
conn = sqlite3.connect('/app/data/applications.db')
cur = conn.cursor()
cur.execute('SELECT id, name FROM applications')
existing = {r[1]: r[0] for r in cur.fetchall()}
configured = set()
for p in projects:
    n = p.get('name','').strip()
    if not n: continue
    configured.add(n)
    if n in existing:
        cur.execute('UPDATE applications SET description=?,url=?,icon_url=?,sort_order=? WHERE id=?',
            (p.get('description',''),p.get('url',''),p.get('icon_url',''),p.get('sort_order',0),existing[n]))
        print(f'  UPDATE {n}')
    else:
        cur.execute('INSERT INTO applications(name,description,url,icon_url,sort_order,created_at,updated_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)',
            (n,p.get('description',''),p.get('url',''),p.get('icon_url',''),p.get('sort_order',0)))
        print(f'  INSERT {n}')
for n, pid in list(existing.items()):
    if n not in configured:
        cur.execute('DELETE FROM applications WHERE id=?', (pid,))
        print(f'  DELETE {n}')
conn.commit()
conn.close()
print(f'  Done. {len(projects)} projects synced.')
"
else
    echo "[entrypoint] No project config found at $CONFIG_FILE"
fi

echo "[entrypoint] Starting uvicorn..."
exec uvicorn main:app --host 0.0.0.0 --port 8000 --proxy-headers
