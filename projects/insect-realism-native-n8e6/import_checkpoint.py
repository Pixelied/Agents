#!/usr/bin/env python3
"""Import only exact approved source blobs from the verified continuation ZIP.

This is a transport/verification helper, not a release qualification certificate.
It never extracts an arbitrary ZIP path and never replaces an existing directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tempfile
import zipfile

EXPECTED_ARCHIVE_SHA256 = 'ac7985166f1acedfaed3c4ce04706b71af0aaa18b7aee284185480ecb77afbca'
ARCHIVE_ROOT = 'insect-realism-desktop-app/'
MAX_ARCHIVE_BYTES = 50 * 1024 * 1024
MAX_EXPANDED_BYTES = 100 * 1024 * 1024
MAX_FILE_BYTES = 10 * 1024 * 1024


def parse_manifest(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip():
            continue
        fields = line.split(' ', 1)
        if len(fields) != 2 or not re.fullmatch(r'[0-9a-f]{40}', fields[0]):
            raise ValueError(f'Invalid Git blob hash at manifest line {number}')
        sha, name = fields
        parts = name.split('/')
        if (not name or name.startswith('/') or '\\' in name or ':' in name
                or any(p in ('', '.', '..', '.git') for p in parts)
                or PurePosixPath(name).suffix.lower() in ('.ttf', '.otf', '.woff', '.woff2')):
            raise ValueError(f'Unsafe or excluded source path at manifest line {number}')
        if name in entries:
            raise ValueError(f'Duplicate source path: {name}')
        entries[name] = sha
    if not entries:
        raise ValueError('Empty source manifest')
    return entries


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def stage_checkpoint(archive: Path, manifest: Path, destination: Path,
                     expected_sha256: str) -> dict:
    """Atomically stage an exact source subset, or leave destination absent."""
    archive, manifest, destination = map(Path, (archive, manifest, destination))
    if destination.exists() or destination.is_symlink():
        raise FileExistsError(f'Refusing to replace existing destination: {destination}')
    if not re.fullmatch(r'[0-9a-f]{64}', expected_sha256):
        raise ValueError('Invalid expected SHA-256')
    if archive.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError('Archive exceeds size limit')
    actual = sha256_file(archive)
    if actual != expected_sha256:
        raise ValueError(f'Archive SHA-256 mismatch: expected {expected_sha256}, got {actual}')
    entries = parse_manifest(manifest)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix='.insect-import-', dir=destination.parent))
    try:
        with zipfile.ZipFile(archive) as z:
            if sum(i.file_size for i in z.infolist()) > MAX_EXPANDED_BYTES:
                raise ValueError('Archive expanded size exceeds limit')
            members: dict[str, zipfile.ZipInfo] = {}
            for info in z.infolist():
                if info.filename in members:
                    raise ValueError(f'Duplicate ZIP member: {info.filename}')
                members[info.filename] = info
            for name, expected_blob in entries.items():
                info = members.get(ARCHIVE_ROOT + name)
                if info is None:
                    raise ValueError(f'Missing source file: {name}')
                file_type = stat.S_IFMT(info.external_attr >> 16)
                if (info.is_dir() or file_type not in (0, stat.S_IFREG)
                        or info.flag_bits & 1 or info.file_size > MAX_FILE_BYTES):
                    raise ValueError(f'Unsafe source member: {name}')
                payload = z.read(info)
                blob = hashlib.sha1(b'blob ' + str(len(payload)).encode('ascii') + b'\0' + payload).hexdigest()
                if blob != expected_blob:
                    raise ValueError(f'Git blob mismatch for {name}: expected {expected_blob}, got {blob}')
                target = temporary.joinpath(*name.split('/'))
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(payload)
                target.chmod(0o755 if (info.external_attr >> 16) & 0o111 else 0o644)
        result = {
            'schema_version': 1,
            'source_archive_sha256': actual,
            'source_manifest_sha256': sha256_file(manifest),
            'imported_files': len(entries),
            'every_git_blob_verified': True,
            'native_build_verified': False,
            'native_input_safety_verified': False,
            'release_qualified': False,
        }
        (temporary / 'SOURCE_IMPORT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        if destination.exists() or destination.is_symlink():
            raise FileExistsError(f'Destination appeared during import: {destination}')
        os.rename(temporary, destination)
        return result
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    # This entrypoint is deliberately locked to the exact user-approved archive.
    entries = parse_manifest(args.manifest)
    if len(entries) != 219:
        raise SystemExit('Expected the 219-file verified source manifest')
    result = stage_checkpoint(args.archive, args.manifest, args.output, EXPECTED_ARCHIVE_SHA256)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
