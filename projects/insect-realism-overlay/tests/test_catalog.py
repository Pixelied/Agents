from datetime import date
from pathlib import Path

import pytest

from insect_research.catalog import append_source, catalog_page_equivalent, read_sources
from insect_research.schema import LicenseClass, SourceKind, SourceRecord


def sample_record() -> SourceRecord:
    return SourceRecord(
        source_id="doi-example",
        title="Measured ant gait",
        kind=SourceKind.PAPER,
        canonical_url="https://doi.org/10.0000/example",
        stable_identifier="10.0000/example",
        retrieved_on=date(2026, 8, 25),
        license_name="reference only",
        license_class=LicenseClass.REFERENCE_ONLY,
        page_equivalent=9,
    )


def test_append_source_round_trips_and_rejects_duplicate_id(tmp_path: Path) -> None:
    path = tmp_path / "sources.csv"
    append_source(path, sample_record())
    assert read_sources(path)[0].stable_identifier == "10.0000/example"
    with pytest.raises(ValueError, match="duplicate source_id"):
        append_source(path, sample_record())


def test_catalog_page_equivalent_sums_reviewed_depth() -> None:
    assert catalog_page_equivalent([sample_record(), sample_record()]) == 18
