"""Persist and compare evaluation reports.

The evaluation scripts print a snapshot to stdout; ``write_report`` saves the
same snapshot as JSON under ``scripts/reports/`` so successive runs can be
compared (or charted) without rerunning the expensive live/judge calls.
"""

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

# In the container /app/data is the persisted volume, so reports survive
# rebuilds and restarts; when run from a host checkout, fall back next to the
# script. EVAL_REPORT_DIR overrides both.
_DEFAULT_DIR = (
    Path("/app/data/reports")
    if Path("/app/data").is_dir()
    else Path(__file__).resolve().parent / "reports"
)
REPORT_DIR = Path(os.environ.get("EVAL_REPORT_DIR", _DEFAULT_DIR))


def build_report(
    kind: str,
    stats: dict,
    rows: list,
    meta: Optional[dict] = None,
) -> dict:
    """A timestamped report envelope around the metric snapshot."""
    return {
        "kind": kind,
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "stats": stats,
        "rows": rows,
        **(meta or {}),
    }


def write_report(report: dict, path: Optional[str] = None) -> Path:
    """Write ``report`` to ``path`` (or a timestamped file in REPORT_DIR)."""
    if path and path != "auto":
        target = Path(path)
    else:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        target = REPORT_DIR / f"{report.get('kind', 'eval')}-{stamp}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return target


def load_report(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def compare_stats(current: dict, previous: dict) -> list[dict]:
    """Numeric metric deltas between two snapshots, sorted by metric name."""
    deltas = []
    for metric in sorted(set(current) | set(previous)):
        before = previous.get(metric)
        after = current.get(metric)
        if not isinstance(before, (int, float)) or not isinstance(after, (int, float)):
            continue
        if isinstance(before, bool) or isinstance(after, bool):
            continue
        deltas.append(
            {
                "metric": metric,
                "previous": before,
                "current": after,
                "delta": round(after - before, 4),
            }
        )
    return deltas


def format_comparison(deltas: list[dict], previous_path: str) -> str:
    if not deltas:
        return "（没有可比较的数值指标）"
    lines = [f"compared with: {os.path.basename(previous_path)}"]
    lines.append(f"{'metric':<28} {'previous':>10} {'current':>10} {'delta':>9}")
    for item in deltas:
        sign = "+" if item["delta"] > 0 else ""
        delta_text = f"{sign}{item['delta']:.3f}"
        lines.append(
            f"{item['metric']:<28} {item['previous']:>10.3f} "
            f"{item['current']:>10.3f} {delta_text:>9}"
        )
    return "\n".join(lines)
