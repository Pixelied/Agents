from dataclasses import dataclass
from datetime import date
from enum import StrEnum


class LicenseClass(StrEnum):
    REDISTRIBUTABLE_AND_SHIPPABLE = "REDISTRIBUTABLE_AND_SHIPPABLE"
    REDISTRIBUTABLE_RESEARCH_ONLY = "REDISTRIBUTABLE_RESEARCH_ONLY"
    REFERENCE_ONLY = "REFERENCE_ONLY"
    DO_NOT_USE = "DO_NOT_USE"


class SourceKind(StrEnum):
    PAPER = "paper"
    DATASET = "dataset"
    MUSEUM = "museum"
    REPOSITORY = "repository"
    ASSET = "asset"
    VIDEO = "video"
    DOCUMENTATION = "documentation"


@dataclass(frozen=True, slots=True)
class SourceRecord:
    source_id: str
    title: str
    kind: SourceKind
    canonical_url: str
    stable_identifier: str
    retrieved_on: date
    license_name: str
    license_class: LicenseClass
    page_equivalent: int
    notes_path: str = ""
    sha256: str = ""

    def __post_init__(self) -> None:
        if not self.source_id.strip():
            raise ValueError("source_id must not be empty")
        if not self.canonical_url.startswith(("https://", "http://")):
            raise ValueError("canonical_url must be HTTP(S)")
        if self.page_equivalent < 0:
            raise ValueError("page_equivalent must be non-negative")


@dataclass(frozen=True, slots=True)
class EvidenceObservation:
    observation_id: str
    domain: str
    species: str
    behavior_state: str
    statement: str
    source_ids: tuple[str, ...]
    confidence: str


@dataclass(frozen=True, slots=True)
class DerivedValue:
    parameter: str
    species: str
    state: str
    value: float
    unit: str
    source_ids: tuple[str, ...]
    uncertainty: str = ""

    def __post_init__(self) -> None:
        if not self.source_ids:
            raise ValueError("source_ids must contain at least one source")


SOURCE_FIELDS = tuple(SourceRecord.__dataclass_fields__)
LICENSE_FIELDS = (
    "source_id",
    "license_name",
    "license_class",
    "redistribution_allowed",
    "shipping_allowed",
    "attribution_required",
    "license_url",
    "review_notes",
)
