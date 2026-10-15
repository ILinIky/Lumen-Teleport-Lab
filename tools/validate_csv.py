"""Validate UTF-8 CSV schema, linkage, order, types and Pandas ingestion. No network."""
import csv
import re
from pathlib import Path
import pandas as pd

root = Path(__file__).resolve().parents[1] / "examples"
a = pd.read_csv(root / "teleport_attempts.csv", encoding="utf-8", keep_default_na=True)
e = pd.read_csv(root / "teleport_events.csv", encoding="utf-8", keep_default_na=True)
schema = (root.parent / "docs" / "CSV_SCHEMA.md").read_text(encoding="utf-8")
attempt_table = schema.split("## teleport_attempts.csv", 1)[1].split("## teleport_events.csv", 1)[0]
event_table = schema.split("## teleport_events.csv", 1)[1].split("### Ereignisse", 1)[0]
assert list(a.columns) == re.findall(r"^\| ([a-z][a-z0-9_]*) \|", attempt_table, re.M), "attempt schema differs from contract"
assert list(e.columns) == re.findall(r"^\| ([a-z][a-z0-9_]*) \|", event_table, re.M), "event schema differs from contract"
assert len(a) >= 8 and not a.attempt_id.duplicated().any()
assert set(e.attempt_id) == set(a.attempt_id)
assert set(a.status) == {"SUCCESS_STABLE", "CORRECTED", "PARTIAL", "TIMEOUT", "UNKNOWN"}
assert e.event_utc_ms.is_monotonic_increasing
assert pd.to_datetime(a.started_utc, utc=True).notna().all()
assert pd.to_datetime(e.event_utc, utc=True).notna().all()
assert a.server_id.str.fullmatch(r"srv_[0-9a-f]{24}").all()
for attempt_id, events in e.groupby("attempt_id", sort=False):
    assert events.elapsed_ms.is_monotonic_increasing
    assert list(events.sequence) == list(range(len(events)))
    assert events.event_type.iloc[0] == "START" and events.event_type.iloc[-1] == "FINISH"
    attempt = a[a.attempt_id == attempt_id].iloc[0]
    assert (events.event_utc_ms - attempt.started_utc_ms == events.elapsed_ms).all()
    if attempt.status == "SUCCESS_STABLE":
        assert attempt.server_target_observed
        assert attempt.correction_count == 0 and not attempt.sample_gap
        assert list(events.loc[events.event_type == "CHECKPOINT", "scheduled_checkpoint_ms"]) == [1000, 3000, 5000, 10000]
for name in ("teleport_attempts.csv", "teleport_events.csv"):
    with (root / name).open(encoding="utf-8", newline="") as source:
        rows = list(csv.reader(source, strict=True))
    assert all(len(row) == len(rows[0]) for row in rows), f"ragged CSV: {name}"
    content = (root / name).read_text(encoding="utf-8")
    assert "example.invalid" not in content and "25565" not in content
assert a.loc[a.origin_dimension != a.target_dimension, "total_distance_blocks"].isna().all()
print(f"PASS Pandas {pd.__version__}: {len(a)} attempts, {len(e)} events; schema, UTC, numbers, IDs, privacy and event order")
print(a.groupby("status").size().to_string())
