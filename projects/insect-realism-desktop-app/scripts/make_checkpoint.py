#!/usr/bin/env python3
"""Atomically save source and evidence; hash exactly the bytes archived, even during a live soak."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import stat
from pathlib import Path
import tempfile
import zipfile

EXCLUDE_PARTS = {'.git', '.cargo', 'target', 'vendor', '__pycache__', 'dist', '.tools'}
EXCLUDE_SUFFIXES = {'.ttf', '.otf', '.ttc', '.woff', '.woff2', '.zip', '.bundle', '.pyc'}

def checkpoint(root: Path, output: Path) -> dict:
    root = root.resolve(strict=True)
    output = output.resolve()
    if output.suffix.lower() != '.zip':
        raise ValueError('Checkpoint output must be a .zip file, not a source file')
    output.parent.mkdir(parents=True, exist_ok=True)
    files = sorted(p for p in root.rglob('*') if p.is_file() and not p.is_symlink()
                   and not set(p.relative_to(root).parts).intersection(EXCLUDE_PARTS)
                   and p.suffix.lower() not in EXCLUDE_SUFFIXES and p.resolve() != output
                   and p.resolve().is_relative_to(root))
    manifest = {}
    fd, temporary_name = tempfile.mkstemp(prefix='.checkpoint-', suffix='.zip', dir=output.parent)
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for path in files:
                # Live JSON reports are atomically replaced by the soak. One read provides a
                # consistent file snapshot; never hash a first read and archive a second one.
                data = path.read_bytes()
                relative = path.relative_to(root).as_posix()
                manifest[relative] = hashlib.sha256(data).hexdigest()
                # Source mtimes are not part of the checkpoint identity. Keep meaningful
                # executable permissions, but avoid carrying machine-specific mode bits.
                info = zipfile.ZipInfo(root.name + '/' + relative, (1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                mode = 0o755 if path.stat().st_mode & 0o111 else 0o644
                info.external_attr = (stat.S_IFREG | mode) << 16
                archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
            info = zipfile.ZipInfo('CHECKPOINT_SHA256.json', (1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            archive.writestr(info, json.dumps(manifest, indent=2) + '\n',
                             compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
        with zipfile.ZipFile(temporary) as archive:
            bad = archive.testzip()
            if bad:
                raise ValueError('Corrupt archive entry: ' + bad)
            for name, digest in manifest.items():
                if hashlib.sha256(archive.read(root.name + '/' + name)).hexdigest() != digest:
                    raise ValueError('Checkpoint digest mismatch: ' + name)
        # Windows _commit (os.fsync) requires a writable descriptor.
        with temporary.open('r+b') as file:
            os.fsync(file.fileno())
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    return {'file': str(output), 'files': len(files), 'bytes': output.stat().st_size,
            'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'all_file_checksums_verified': True}

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(checkpoint(Path(__file__).resolve().parents[1], args.output)))

if __name__ == '__main__':
    main()
