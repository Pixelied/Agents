"""Initialize a staging tree. Install the package before running this script."""
import argparse
from pathlib import Path
from insect_research.paths import ARCHIVE_ROOT, initialize_pack


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ARCHIVE_ROOT)
    args = parser.parse_args()
    initialize_pack(args.root)
    print(args.root.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
