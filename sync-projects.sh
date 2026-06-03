#!/bin/bash
# Sync projects from config/projects.json into the running backend container.
# Run this from the project root.
set -e

CONFIG_FILE="config/projects.json"
CONTAINER_CONFIG="/tmp/projects.json"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: $CONFIG_FILE not found. Run from project root."
  exit 1
fi

echo "Syncing projects from $CONFIG_FILE into backend container..."

docker compose cp "$CONFIG_FILE" "backend:${CONTAINER_CONFIG}"

docker compose exec backend python3 - "${CONTAINER_CONFIG}" <<'PYEOF'
import json, sqlite3, sys

config_path = sys.argv[1]
db_path = "/app/data/applications.db"

with open(config_path) as f:
    projects = json.load(f)

conn = sqlite3.connect(db_path)
cur = conn.cursor()

cur.execute("SELECT id, name FROM applications")
existing = {row[1]: row[0] for row in cur.fetchall()}

configured = set()

for p in projects:
    name = p.get("name", "").strip()
    if not name:
        continue
    configured.add(name)

    if name in existing:
        cur.execute(
            "UPDATE applications SET description=?, url=?, icon_url=?, sort_order=? WHERE id=?",
            (p.get("description", ""), p.get("url", ""), p.get("icon_url", ""),
             p.get("sort_order", 0), existing[name])
        )
        print(f"  UPDATE {name}")
    else:
        cur.execute(
            "INSERT INTO applications (name, description, url, icon_url, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            (name, p.get("description", ""), p.get("url", ""), p.get("icon_url", ""), p.get("sort_order", 0))
        )
        print(f"  INSERT {name}")

for name, pid in list(existing.items()):
    if name not in configured:
        cur.execute("DELETE FROM applications WHERE id=?", (pid,))
        print(f"  DELETE {name}")

conn.commit()
conn.close()
print(f"\nDone. {len(projects)} projects synced.")
PYEOF

docker compose exec backend rm -f "${CONTAINER_CONFIG}"
