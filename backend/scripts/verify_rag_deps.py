"""Verify that RAG dependencies are installed (called during Docker build)."""
import sys

packages = ['sqlite_vec', 'fastembed']
missing = []
for p in packages:
    try:
        __import__(p)
        print(f'[verify] {p} OK')
    except ImportError:
        missing.append(p)
        print(f'[verify] WARNING: {p} not installed')

if missing:
    print(f'[verify] Missing: {", ".join(missing)} — run pip install -r requirements.txt')
    sys.exit(0)  # non-fatal: build continues but RAG won't work
