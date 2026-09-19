#!/usr/bin/env python3
"""Write checksums for the exact release uploads. Integrity does not certify native behavior."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import tempfile


def write_checksums(assets: list[Path], output: Path) -> None:
    if not assets:
        raise ValueError('release must contain at least one asset')
    resolved_output = output.resolve()
    unique: dict[str, Path] = {}
    for asset in assets:
        if asset.is_symlink() or not asset.is_file():
            raise ValueError('asset must be a regular file: ' + str(asset))
        if any(character in asset.name for character in ('\n', '\r', '\\')):
            raise ValueError('unsafe asset name for a portable checksum file')
        key = asset.name.casefold()
        if key in unique:
            raise ValueError('duplicate release asset filename: ' + asset.name)
        if asset.resolve() == resolved_output or key == output.name.casefold():
            raise ValueError('checksum output must not overwrite or shadow an asset')
        unique[key] = asset
    lines = []
    for asset in sorted(unique.values(), key=lambda path: path.name):
        digest = hashlib.sha256()
        with asset.open('rb') as file:
            for block in iter(lambda: file.read(1024 * 1024), b''):
                digest.update(block)
        lines.append(digest.hexdigest() + '  ' + asset.name + '\n')
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', newline='\n',
                                         prefix='.release-hashes-', dir=output.parent,
                                         delete=False) as file:
            temporary = Path(file.name)
            file.writelines(lines)
            file.flush()
            os.fsync(file.fileno())
        os.replace(temporary, output)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('assets', nargs='+', type=Path)
    args = parser.parse_args()
    write_checksums(args.assets, args.output)
    print(args.output)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError) as error:
        raise SystemExit('Release checksum generation failed: ' + str(error))
