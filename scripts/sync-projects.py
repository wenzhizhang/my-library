#!/usr/bin/env python3
"""
Sync projects from config/projects.json into the applications database.
Usage: python scripts/sync-projects.py [--db /app/data/demo.db]
"""
import json
import sqlite3
import sys
import os

CONFIG_PATH = os.path.join(os.path.dirname(__file__), '..', 'config', 'projects.json')


def sync(db_path: str):
    with open(CONFIG_PATH) as f:
        projects = json.load(f)

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Get existing projects by name
    cur.execute("SELECT id, name FROM applications")
    existing = {row[1]: row[0] for row in cur.fetchall()}

    configured_names = set()

    for p in projects:
        name = p.get("name", "").strip()
        if not name:
            continue
        configured_names.add(name)

        data = {
            "name": name,
            "description": p.get("description", ""),
            "url": p.get("url", ""),
            "icon_url": p.get("icon_url", ""),
            "sort_order": p.get("sort_order", 0),
        }

        if name in existing:
            # Update
            cur.execute(
                "UPDATE applications SET description=?, url=?, icon_url=?, sort_order=? WHERE id=?",
                (data["description"], data["url"], data["icon_url"], data["sort_order"], existing[name])
            )
            print(f"  UPDATE {name}")
        else:
            # Insert
            cur.execute(
                "INSERT INTO applications (name, description, url, icon_url, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                (data["name"], data["description"], data["url"], data["icon_url"], data["sort_order"])
            )
            print(f"  INSERT {name}")

    # Remove projects that exist in DB but not in config
    for name, pid in existing.items():
        if name not in configured_names:
            cur.execute("DELETE FROM applications WHERE id=?", (pid,))
            print(f"  DELETE {name}")

    conn.commit()
    conn.close()
    print(f"\nDone. {len(projects)} projects synced.")


if __name__ == '__main__':
    db_path = sys.argv[1] if len(sys.argv) > 1 else None
    if not db_path:
        # Try common paths
        for p in ['/app/data/demo.db', 'backend/data/demo.db']:
            if os.path.exists(p):
                db_path = p
                break
    if not db_path:
        print("Usage: sync-projects.py <path-to-demo.db>")
        sys.exit(1)
    sync(db_path)
