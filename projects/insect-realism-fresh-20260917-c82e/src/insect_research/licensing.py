"""Explicit project inclusion policy, not an automatic license detector."""
import csv
from dataclasses import dataclass
from pathlib import Path

from ._io import atomic_text_writer, exclusive_lock
from .schema import LICENSE_FIELDS, LicenseClass, require_text, validate_http_url, validate_source_id


@dataclass(frozen=True, slots=True)
class InclusionDecision:
    bundle_bytes: bool
    ship_with_app: bool
    may_derive_facts: bool


_POLICY = {
    LicenseClass.REDISTRIBUTABLE_AND_SHIPPABLE: InclusionDecision(True, True, True),
    LicenseClass.REDISTRIBUTABLE_RESEARCH_ONLY: InclusionDecision(True, False, True),
    LicenseClass.REFERENCE_ONLY: InclusionDecision(False, False, True),
    LicenseClass.DO_NOT_USE: InclusionDecision(False, False, False),
}


def decide_inclusion(license_class: LicenseClass) -> InclusionDecision:
    if not isinstance(license_class, LicenseClass):
        raise ValueError("license_class must be an explicitly selected LicenseClass")
    return _POLICY[license_class]


@dataclass(frozen=True, slots=True)
class LicenseRecord:
    source_id: str
    license_name: str
    license_class: LicenseClass
    redistribution_allowed: bool
    shipping_allowed: bool
    attribution_required: bool
    license_url: str
    review_notes: str

    def __post_init__(self) -> None:
        validate_source_id(self.source_id)
        require_text(self.license_name, "license_name")
        require_text(self.review_notes, "review_notes")
        decision = decide_inclusion(self.license_class)
        for field in ("redistribution_allowed", "shipping_allowed", "attribution_required"):
            if type(getattr(self, field)) is not bool:
                raise ValueError(f"{field} must be a boolean")
        if (self.redistribution_allowed, self.shipping_allowed) != (decision.bundle_bytes, decision.ship_with_app):
            raise ValueError("license manifest permission flags contradict the inclusion policy")
        if not isinstance(self.license_url, str):
            raise ValueError("license_url must be text")
        if self.license_url:
            validate_http_url(self.license_url)
        elif decision.bundle_bytes:
            raise ValueError("redistributable material requires a verified license_url")


def _unique(records: list[LicenseRecord]) -> None:
    seen: set[str] = set()
    for record in records:
        if not isinstance(record, LicenseRecord):
            raise ValueError("license entries must be LicenseRecord objects")
        if record.source_id in seen:
            raise ValueError(f"duplicate license source_id: {record.source_id}")
        seen.add(record.source_id)


def write_license_manifest(path: Path, records: list[LicenseRecord]) -> None:
    records = list(records)
    _unique(records)
    path = Path(path)
    with exclusive_lock(path), atomic_text_writer(path) as handle:
        writer = csv.DictWriter(handle, fieldnames=LICENSE_FIELDS, lineterminator="\n")
        writer.writeheader()
        for record in sorted(records, key=lambda record: record.source_id):
            row = {field: getattr(record, field) for field in LICENSE_FIELDS}
            row["license_class"] = record.license_class.value
            for field in ("redistribution_allowed", "shipping_allowed", "attribution_required"):
                row[field] = str(row[field]).lower()
            writer.writerow(row)


def read_license_manifest(path: Path) -> list[LicenseRecord]:
    path = Path(path)
    if path.is_symlink():
        raise ValueError("license manifest must not be a symlink")
    if not path.exists():
        return []
    records: list[LicenseRecord] = []
    try:
        with path.open(newline="", encoding="utf-8-sig") as handle:
            reader = csv.DictReader(handle, strict=True)
            if reader.fieldnames != list(LICENSE_FIELDS):
                raise ValueError("license manifest header must match LICENSE_FIELDS exactly")
            for row in reader:
                if set(row) != set(LICENSE_FIELDS) or any(value is None for value in row.values()):
                    raise ValueError("malformed license manifest row")
                values = dict(row)
                values["license_class"] = LicenseClass(values["license_class"])
                for field in ("redistribution_allowed", "shipping_allowed", "attribution_required"):
                    if values[field] not in {"true", "false"}:
                        raise ValueError(f"{field} must be a CSV boolean (true or false)")
                    values[field] = values[field] == "true"
                records.append(LicenseRecord(**values))
    except csv.Error as exc:
        raise ValueError(f"malformed license manifest CSV: {exc}") from exc
    _unique(records)
    return records
