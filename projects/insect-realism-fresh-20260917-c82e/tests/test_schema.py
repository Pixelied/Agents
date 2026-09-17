from dataclasses import FrozenInstanceError, replace
from datetime import date, datetime
import math

import pytest
from insect_research.schema import (
    DerivedValue, EvidenceObservation, LicenseClass, SourceKind, SourceRecord,
    SOURCE_FIELDS, LICENSE_FIELDS,
)


def source_record(**changes):
    values = dict(source_id="paper-ant-gait", title="Measured ant gait", kind=SourceKind.PAPER,
                  canonical_url="https://doi.org/10.0000/example", stable_identifier="10.0000/example",
                  retrieved_on=date(2026, 9, 17), license_name="Publisher terms",
                  license_class=LicenseClass.REFERENCE_ONLY, page_equivalent=0)
    values.update(changes)
    return SourceRecord(**values)


def test_source_record_has_exact_csv_contract_and_is_immutable():
    record = source_record()
    assert SOURCE_FIELDS == ("source_id", "title", "kind", "canonical_url", "stable_identifier", "retrieved_on", "license_name", "license_class", "page_equivalent", "notes_path", "sha256")
    assert LICENSE_FIELDS == ("source_id", "license_name", "license_class", "redistribution_allowed", "shipping_allowed", "attribution_required", "license_url", "review_notes")
    with pytest.raises(FrozenInstanceError):
        record.title = "changed"


@pytest.mark.parametrize("field,value", [
    ("source_id", ""), ("source_id", "../escape"), ("source_id", "a;b"),
    ("title", " "), ("stable_identifier", ""), ("license_name", ""),
    ("canonical_url", "ftp://example.org/x"), ("canonical_url", "https://"),
    ("canonical_url", "https://user:secret@example.org/x"),
    ("canonical_url", "https://example.org:bad/x"),
    ("canonical_url", "https://example.org/a b"),
    ("canonical_url", "https://example.org/\nx"),
    ("page_equivalent", -1), ("page_equivalent", 2.5), ("page_equivalent", True),
    ("retrieved_on", "2026-09-17"), ("retrieved_on", datetime(2026, 9, 17)),
    ("kind", "paper"), ("license_class", "REFERENCE_ONLY"),
    ("sha256", "x" * 64), ("sha256", "a" * 63),
    ("notes_path", "../outside.md"), ("notes_path", "/outside.md"),
    ("notes_path", "notes\\outside.md"), ("notes_path", "notes/./x.md"),
])
def test_source_rejects_invalid_fields(field, value):
    with pytest.raises(ValueError):
        source_record(**{field: value})


def test_source_accepts_verified_hash_and_relative_note():
    record = source_record(sha256="ab" * 32, notes_path="research/source_notes/paper-ant-gait.md")
    assert record.sha256 == "ab" * 32


def test_every_license_class_is_explicit():
    assert {x.value for x in LicenseClass} == {"REDISTRIBUTABLE_AND_SHIPPABLE", "REDISTRIBUTABLE_RESEARCH_ONLY", "REFERENCE_ONLY", "DO_NOT_USE"}


@pytest.mark.parametrize("ids", [(), ("",), ("a", "a"), "a", ["a"], ("a;b",)])
def test_derived_values_require_unambiguous_immutable_provenance(ids):
    with pytest.raises(ValueError, match="source_ids"):
        DerivedValue("speed", "Example ant", "walking", 2.0, "mm/s", ids)


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf, True, "2"])
def test_derived_values_reject_nonfinite_or_non_numeric_values(value):
    with pytest.raises(ValueError, match="value"):
        DerivedValue("speed", "Example ant", "walking", value, "mm/s", ("paper-a",))


def test_signed_acceleration_is_valid_and_units_are_required():
    record = DerivedValue("acceleration", "Example ant", "walking", -2.0, "mm/s2", ("paper-a",), "reported range")
    assert record.value == -2.0
    with pytest.raises(ValueError, match="unit"):
        replace(record, unit="")


def test_observations_require_source_and_confidence():
    observation = EvidenceObservation("obs-1", "gait", "Example ant", "walking", "Tripod pattern observed.", ("paper-a",), "high")
    assert observation.confidence == "high"
    with pytest.raises(ValueError, match="source_ids"):
        replace(observation, source_ids=())
    with pytest.raises(ValueError, match="confidence"):
        replace(observation, confidence="certain")
