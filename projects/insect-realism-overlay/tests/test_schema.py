from datetime import date

import pytest

from insect_research.schema import (
    DerivedValue,
    LicenseClass,
    SourceKind,
    SourceRecord,
)


def test_source_record_requires_stable_source_identity() -> None:
    record = SourceRecord(
        source_id="doi-10.0000-example",
        title="Example ant biomechanics paper",
        kind=SourceKind.PAPER,
        canonical_url="https://doi.org/10.0000/example",
        stable_identifier="10.0000/example",
        retrieved_on=date(2026, 8, 25),
        license_name="Publisher terms",
        license_class=LicenseClass.REFERENCE_ONLY,
        page_equivalent=12,
    )
    assert record.page_equivalent == 12


def test_derived_value_rejects_value_without_source() -> None:
    with pytest.raises(ValueError, match="source_ids"):
        DerivedValue(
            parameter="walking_speed_mm_s",
            species="Linepithema humile",
            state="walking",
            value=20.0,
            unit="mm/s",
            source_ids=(),
        )
