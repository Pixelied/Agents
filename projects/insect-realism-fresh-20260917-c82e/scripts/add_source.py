"""Add one explicitly classified source. This command never guesses a license."""
import argparse
from datetime import date
from pathlib import Path

from insect_research.catalog import append_source
from insect_research.paths import ARCHIVE_ROOT
from insect_research.schema import LicenseClass, SourceKind, SourceRecord


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ARCHIVE_ROOT)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--kind", choices=[x.value for x in SourceKind], required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--stable-identifier", required=True)
    parser.add_argument("--retrieved-on", type=date.fromisoformat, required=True)
    parser.add_argument("--license-name", required=True)
    parser.add_argument("--license-class", choices=[x.value for x in LicenseClass], required=True)
    parser.add_argument("--page-equivalent", type=int, required=True)
    parser.add_argument("--notes-path", default="")
    parser.add_argument("--sha256", default="")
    args = parser.parse_args()
    try:
        record = SourceRecord(args.source_id, args.title, SourceKind(args.kind), args.url,
                              args.stable_identifier, args.retrieved_on, args.license_name,
                              LicenseClass(args.license_class), args.page_equivalent, args.notes_path, args.sha256)
        append_source(args.root / "00_MASTER_INDEX/SOURCE_CATALOG.csv", record)
    except (ValueError, RuntimeError, OSError) as exc:
        parser.error(str(exc))
    print(f"Recorded source: {record.source_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
