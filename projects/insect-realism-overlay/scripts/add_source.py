import argparse
from datetime import date

from insect_research.catalog import append_source
from insect_research.paths import ARCHIVE_ROOT
from insect_research.schema import LicenseClass, SourceKind, SourceRecord


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--kind", choices=[k.value for k in SourceKind], required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--stable-identifier", required=True)
    parser.add_argument("--retrieved-on", type=date.fromisoformat, required=True)
    parser.add_argument("--license-name", required=True)
    parser.add_argument("--license-class", choices=[c.value for c in LicenseClass], required=True)
    parser.add_argument("--page-equivalent", type=int, required=True)
    parser.add_argument("--notes-path", default="")
    parser.add_argument("--sha256", default="")
    args = parser.parse_args()
    append_source(
        ARCHIVE_ROOT / "00_MASTER_INDEX" / "SOURCE_CATALOG.csv",
        SourceRecord(
            source_id=args.source_id,
            title=args.title,
            kind=SourceKind(args.kind),
            canonical_url=args.url,
            stable_identifier=args.stable_identifier,
            retrieved_on=args.retrieved_on,
            license_name=args.license_name,
            license_class=LicenseClass(args.license_class),
            page_equivalent=args.page_equivalent,
            notes_path=args.notes_path,
            sha256=args.sha256,
        ),
    )


if __name__ == "__main__":
    main()
