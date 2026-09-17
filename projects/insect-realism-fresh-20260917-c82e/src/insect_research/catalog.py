"""Validated, deterministic source intake with duplicate-identity protection."""
from collections.abc import Iterable
import csv
from datetime import date
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

from ._io import atomic_text_writer, exclusive_lock
from .schema import LicenseClass, SourceKind, SourceRecord, SOURCE_FIELDS


def _stable_key(identifier: str) -> str:
    candidate = identifier.strip()
    if candidate.lower().startswith("doi:"):
        candidate = candidate[4:].strip()
    if candidate.lower().startswith(("https://doi.org/", "http://doi.org/", "https://dx.doi.org/", "http://dx.doi.org/")):
        candidate = unquote(urlsplit(candidate).path.lstrip("/"))
    if re.fullmatch(r"10\.\d{4,9}/\S+", candidate, flags=re.IGNORECASE):
        return "doi:" + candidate.casefold()
    return candidate


def _url_key(url: str) -> tuple[object, ...]:
    parts = urlsplit(url)
    if parts.hostname in {"doi.org", "dx.doi.org"}:
        return ("doi", _stable_key(url))
    port = parts.port
    if (parts.scheme, port) in {("http", 80), ("https", 443)}:
        port = None
    # Fragments refer to sections of one document, not independent sources.
    return (parts.scheme, parts.hostname, port, parts.path or "/", parts.query)


def _validate_unique(records: Iterable[SourceRecord]) -> None:
    seen: dict[str, set] = {"source_id": set(), "stable_identifier": set(), "canonical_url": set()}
    for record in records:
        if not isinstance(record, SourceRecord):
            raise ValueError("catalog entries must be SourceRecord objects")
        keys = {"source_id": record.source_id, "stable_identifier": _stable_key(record.stable_identifier),
                "canonical_url": _url_key(record.canonical_url)}
        for field, key in keys.items():
            if key in seen[field]:
                raise ValueError(f"duplicate {field}: {getattr(record, field)}")
            seen[field].add(key)


def read_sources(path: Path) -> list[SourceRecord]:
    path = Path(path)
    if path.is_symlink():
        raise ValueError("catalog must not be a symlink")
    if not path.exists():
        return []
    records: list[SourceRecord] = []
    try:
        with path.open(newline="", encoding="utf-8-sig") as handle:
            reader = csv.DictReader(handle, strict=True)
            if reader.fieldnames != list(SOURCE_FIELDS):
                raise ValueError(f"{path}: catalog header must match SOURCE_FIELDS exactly")
            for row in reader:
                if set(row) != set(SOURCE_FIELDS) or any(v is None for v in row.values()):
                    raise ValueError(f"{path}:{reader.line_num}: malformed catalog row")
                try:
                    records.append(SourceRecord(
                        source_id=row["source_id"], title=row["title"], kind=SourceKind(row["kind"]),
                        canonical_url=row["canonical_url"], stable_identifier=row["stable_identifier"],
                        retrieved_on=date.fromisoformat(row["retrieved_on"]), license_name=row["license_name"],
                        license_class=LicenseClass(row["license_class"]), page_equivalent=int(row["page_equivalent"]),
                        notes_path=row["notes_path"], sha256=row["sha256"],
                    ))
                except (ValueError, TypeError) as exc:
                    raise ValueError(f"{path}:{reader.line_num}: invalid source record: {exc}") from exc
    except csv.Error as exc:
        raise ValueError(f"{path}: malformed CSV: {exc}") from exc
    _validate_unique(records)
    return records


def append_source(path: Path, record: SourceRecord) -> None:
    path = Path(path)
    with exclusive_lock(path):
        records = [*read_sources(path), record]
        _validate_unique(records)
        with atomic_text_writer(path) as handle:
            writer = csv.DictWriter(handle, fieldnames=SOURCE_FIELDS, lineterminator="\n")
            writer.writeheader()
            for item in sorted(records, key=lambda item: item.source_id):
                row = {name: getattr(item, name) for name in SOURCE_FIELDS}
                row.update(kind=item.kind.value, license_class=item.license_class.value, retrieved_on=item.retrieved_on.isoformat())
                writer.writerow(row)


def catalog_page_equivalent(records: Iterable[SourceRecord]) -> int:
    """Sum reviewed depth only for a unique catalog; duplicates are an error."""
    records = tuple(records)
    _validate_unique(records)
    return sum(record.page_equivalent for record in records)
