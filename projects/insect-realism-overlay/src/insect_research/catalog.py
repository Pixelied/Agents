import csv
from datetime import date
from pathlib import Path
from typing import Iterable

from insect_research.schema import LicenseClass, SourceKind, SourceRecord, SOURCE_FIELDS


def read_sources(path: Path) -> list[SourceRecord]:
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    return [
        SourceRecord(
            source_id=row["source_id"],
            title=row["title"],
            kind=SourceKind(row["kind"]),
            canonical_url=row["canonical_url"],
            stable_identifier=row["stable_identifier"],
            retrieved_on=date.fromisoformat(row["retrieved_on"]),
            license_name=row["license_name"],
            license_class=LicenseClass(row["license_class"]),
            page_equivalent=int(row["page_equivalent"]),
            notes_path=row.get("notes_path", ""),
            sha256=row.get("sha256", ""),
        )
        for row in rows
    ]


def append_source(path: Path, record: SourceRecord) -> None:
    existing = read_sources(path)
    if any(row.source_id == record.source_id for row in existing):
        raise ValueError(f"duplicate source_id: {record.source_id}")
    path.parent.mkdir(parents=True, exist_ok=True)
    new_file = not path.exists()
    with path.open("a", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=SOURCE_FIELDS)
        if new_file:
            writer.writeheader()
        data = {field: getattr(record, field) for field in SOURCE_FIELDS}
        data["kind"] = record.kind.value
        data["license_class"] = record.license_class.value
        data["retrieved_on"] = record.retrieved_on.isoformat()
        writer.writerow(data)


def catalog_page_equivalent(records: Iterable[SourceRecord]) -> int:
    return sum(record.page_equivalent for record in records)
