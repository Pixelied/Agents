from dataclasses import replace
from datetime import date
import pytest

from insect_research.licensing import decide_inclusion, LicenseRecord, read_license_manifest, write_license_manifest
from insect_research.schema import LicenseClass, SourceKind, SourceRecord


@pytest.mark.parametrize("license_class,bundle,ship,derive", [
    (LicenseClass.REDISTRIBUTABLE_AND_SHIPPABLE, True, True, True),
    (LicenseClass.REDISTRIBUTABLE_RESEARCH_ONLY, True, False, True),
    (LicenseClass.REFERENCE_ONLY, False, False, True),
    (LicenseClass.DO_NOT_USE, False, False, False),
])
def test_exact_inclusion_policy(license_class, bundle, ship, derive):
    decision = decide_inclusion(license_class)
    assert (decision.bundle_bytes, decision.ship_with_app, decision.may_derive_facts) == (bundle, ship, derive)


@pytest.mark.parametrize("unknown", [None, "MIT", "REFERENCE_ONLY", 0])
def test_unknown_or_untyped_class_is_not_implicitly_allowed(unknown):
    with pytest.raises(ValueError):
        decide_inclusion(unknown)


def row(**overrides):
    values = dict(source_id="paper-a", license_name="Unverified item terms", license_class=LicenseClass.REFERENCE_ONLY,
                  redistribution_allowed=False, shipping_allowed=False, attribution_required=False,
                  license_url="", review_notes="No item-level permission verified; metadata and limited derived observations only.")
    values.update(overrides)
    return LicenseRecord(**values)


@pytest.mark.parametrize("overrides", [
    {"redistribution_allowed": True}, {"shipping_allowed": True},
    {"attribution_required": "false"}, {"review_notes": ""}, {"source_id": ""},
    {"license_class": LicenseClass.DO_NOT_USE, "redistribution_allowed": True},
    {"license_class": LicenseClass.REDISTRIBUTABLE_AND_SHIPPABLE, "redistribution_allowed": True, "shipping_allowed": True, "license_url": ""},
])
def test_license_manifest_cannot_contradict_policy(overrides):
    with pytest.raises(ValueError):
        row(**overrides)


def test_manifest_round_trip_is_sorted_and_byte_deterministic(tmp_path):
    a, b = row(), row(source_id="paper-b")
    first, second = tmp_path / "a.csv", tmp_path / "b.csv"
    write_license_manifest(first, [b, a])
    write_license_manifest(second, [a, b])
    assert first.read_bytes() == second.read_bytes()
    assert read_license_manifest(first) == [a, b]


def test_duplicate_license_rows_fail_without_destroying_manifest(tmp_path):
    path = tmp_path / "licenses.csv"
    write_license_manifest(path, [row()])
    before = path.read_bytes()
    with pytest.raises(ValueError, match="duplicate"):
        write_license_manifest(path, [row(), row()])
    assert path.read_bytes() == before


def test_manifest_rejects_non_boolean_csv_flags(tmp_path):
    path = tmp_path / "licenses.csv"
    write_license_manifest(path, [row()])
    path.write_text(path.read_text(encoding="utf-8").replace("false,false,false", "maybe,false,false"), encoding="utf-8")
    with pytest.raises(ValueError, match="boolean"):
        read_license_manifest(path)


def test_empty_license_manifest_has_a_real_header(tmp_path):
    path = tmp_path / "licenses.csv"
    write_license_manifest(path, [])
    assert path.read_text(encoding="utf-8").startswith("source_id,license_name,")
    assert read_license_manifest(path) == []
