import csv
from pathlib import Path

import pytest

from insect_research.display_scale import mm_to_pixels, pixels_to_mm

ROOT = Path(__file__).resolve().parents[1]
DERIVED = ROOT / "INSECT_REALISM_MEGA_PACK" / "08_DERIVED_BIOLOGY_DATABASE"


def test_mm_to_pixels_uses_physical_inches() -> None:
    assert mm_to_pixels(3.0, 110.0) == pytest.approx(12.9921259843)


def test_round_trip() -> None:
    px = mm_to_pixels(2.5, 218.0)
    assert pixels_to_mm(px, 218.0) == pytest.approx(2.5)


def test_rendering_scale_table_covers_target_lengths_and_ppi() -> None:
    with (DERIVED / "rendering_scale.csv").open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    assert len(rows) == 30
    assert {float(row["body_length_mm"]) for row in rows} == {2.0, 2.5, 3.0, 3.5, 4.0}
    assert {float(row["ppi"]) for row in rows} == {96.0, 110.0, 144.0, 163.0, 218.0, 254.0}
    for row in rows:
        expected = float(row["body_length_mm"]) / 25.4 * float(row["ppi"])
        assert float(row["body_length_px"]) == pytest.approx(expected)


def test_primary_species_is_linepithema_with_traceable_range() -> None:
    with (DERIVED / "species.csv").open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    primary = [row for row in rows if row["primary_target"].lower() == "true"]
    assert len(primary) == 1
    row = primary[0]
    assert row["scientific_name"] == "Linepithema humile"
    assert float(row["worker_body_length_min_mm"]) == pytest.approx(2.2)
    assert float(row["worker_body_length_max_mm"]) == pytest.approx(2.6)
    assert "msstate-linepithema-humile" in row["source_ids"]
    assert "ufifas-argentine-ant" in row["source_ids"]
