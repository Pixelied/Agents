import csv
from pathlib import Path

from insect_research.catalog import catalog_page_equivalent, read_sources

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "INSECT_REALISM_MEGA_PACK"
MASTER = PACK / "00_MASTER_INDEX"
NOTES = ROOT / "research" / "source_notes"

REQUIRED_NOTE_HEADINGS = (
    "## Why this source matters",
    "## Measurements / observations extracted",
    "## Units and uncertainty",
    "## What can enter the derived database",
    "## What must not be redistributed",
    "## Conflicts / limitations",
    "## Follow-up sources cited by this work",
)

PRIORITY_FAMILIES = (
    "anatomy/worker dimensions",
    "gait/tripod/stride",
    "speed/acceleration/pauses",
    "turning/curvature",
    "vertical glass adhesion",
    "antenna kinematics",
    "exploration/random walks",
    "pheromone trail following",
    "encounters/antennation/collision",
    "grouping/lane formation",
    "high-speed footage/pose datasets",
    "micro-CT/specimen imagery",
    "screen/display-scale rendering",
)


def _license_rows() -> dict[str, dict[str, str]]:
    with (MASTER / "LICENSE_MANIFEST.csv").open(newline="", encoding="utf-8") as f:
        return {row["source_id"]: row for row in csv.DictReader(f)}


def test_primary_corpus_meets_depth_and_provenance_gate() -> None:
    records = read_sources(MASTER / "SOURCE_CATALOG.csv")
    assert len(records) >= 30
    assert catalog_page_equivalent(records) >= 2000
    assert len({r.source_id for r in records}) == len(records)
    assert len({r.stable_identifier for r in records}) == len(records)

    licenses = _license_rows()
    assert set(licenses) == {r.source_id for r in records}

    for record in records:
        note = ROOT / record.notes_path
        assert note.is_file(), record.source_id
        text = note.read_text(encoding="utf-8")
        for heading in REQUIRED_NOTE_HEADINGS:
            assert heading in text, f"{record.source_id}: missing {heading}"
        assert f"- Source ID: {record.source_id}" in text
        assert f"- Stable identifier: {record.stable_identifier}" in text
        assert f"- Reviewed page-equivalent: {record.page_equivalent}" in text


def test_priority_matrix_has_multiple_independent_sources_per_family() -> None:
    text = (MASTER / "PRIORITY_SOURCES.md").read_text(encoding="utf-8")
    for family in PRIORITY_FAMILIES:
        matching = [line for line in text.splitlines() if line.startswith(f"| {family} |")]
        assert len(matching) == 1, family
        source_cell = matching[0].split("|")[2].strip()
        ids = [s.strip() for s in source_cell.split(";") if s.strip()]
        assert len(set(ids)) >= 2, f"{family}: {ids}"
