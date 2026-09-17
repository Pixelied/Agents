"""Strict records for evidence, provenance and derived measurements."""
from dataclasses import dataclass
from datetime import date
from enum import StrEnum
import math
from numbers import Real
import re
from urllib.parse import urlsplit

from .paths import validate_relative_path


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


def require_text(value: str, field: str) -> None:
    if not isinstance(value, str) or not value.strip() or "\x00" in value:
        raise ValueError(f"{field} must be non-empty text")


def validate_source_id(value: str) -> None:
    if not isinstance(value, str) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]*", value):
        raise ValueError("source_id must be a lowercase, filename-safe identifier")


def validate_http_url(value: str) -> None:
    if not isinstance(value, str) or any(c.isspace() or ord(c) < 32 for c in value):
        raise ValueError("URL must be a complete HTTP(S) URL without whitespace")
    try:
        parts = urlsplit(value)
        valid = (parts.scheme in {"http", "https"} and bool(parts.hostname)
                 and parts.username is None and parts.password is None)
        _ = parts.port  # Also validates malformed port syntax and range.
    except ValueError as exc:
        raise ValueError("URL must be a valid HTTP(S) URL") from exc
    if not valid:
        raise ValueError("URL must be HTTP(S), absolute, and contain no credentials")


def validate_sha256(value: str, *, allow_empty: bool = True) -> None:
    if allow_empty and value == "":
        return
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", value):
        raise ValueError("sha256 must be a 64-character hexadecimal SHA-256 digest")


def _validate_source_ids(source_ids: tuple[str, ...]) -> None:
    if not isinstance(source_ids, tuple) or not source_ids:
        raise ValueError("source_ids must be a non-empty tuple of source identifiers")
    for source_id in source_ids:
        try:
            validate_source_id(source_id)
        except ValueError as exc:
            raise ValueError("source_ids contains an invalid source identifier") from exc
    if len(set(source_ids)) != len(source_ids):
        raise ValueError("source_ids must not contain duplicates")


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
        validate_source_id(self.source_id)
        for field in ("title", "stable_identifier", "license_name"):
            require_text(getattr(self, field), field)
        validate_http_url(self.canonical_url)
        if not isinstance(self.kind, SourceKind):
            raise ValueError("kind must be a SourceKind")
        if not isinstance(self.license_class, LicenseClass):
            raise ValueError("license_class must be a LicenseClass")
        if type(self.retrieved_on) is not date:
            raise ValueError("retrieved_on must be a date, not a string or datetime")
        if type(self.page_equivalent) is not int or self.page_equivalent < 0:
            raise ValueError("page_equivalent must be a non-negative integer")
        if not isinstance(self.notes_path, str):
            raise ValueError("notes_path must be text")
        if self.notes_path:
            validate_relative_path(self.notes_path)
        validate_sha256(self.sha256)


@dataclass(frozen=True, slots=True)
class EvidenceObservation:
    observation_id: str
    domain: str
    species: str
    behavior_state: str
    statement: str
    source_ids: tuple[str, ...]
    confidence: str

    def __post_init__(self) -> None:
        for field in ("observation_id", "domain", "species", "behavior_state", "statement"):
            require_text(getattr(self, field), field)
        _validate_source_ids(self.source_ids)
        if self.confidence not in {"high", "medium", "low"}:
            raise ValueError("confidence must be high, medium, or low")


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
        for field in ("parameter", "species", "state", "unit"):
            require_text(getattr(self, field), field)
        _validate_source_ids(self.source_ids)
        if isinstance(self.value, bool) or not isinstance(self.value, Real) or not math.isfinite(self.value):
            raise ValueError("value must be a finite real number")
        if not isinstance(self.uncertainty, str):
            raise ValueError("uncertainty must be text")


SOURCE_FIELDS = tuple(SourceRecord.__dataclass_fields__)
LICENSE_FIELDS = (
    "source_id", "license_name", "license_class", "redistribution_allowed",
    "shipping_allowed", "attribution_required", "license_url", "review_notes",
)
