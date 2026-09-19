#!/usr/bin/env python3
"""Reseal a staged package after native signing while preserving build provenance.

This records changed bytes; it does not authenticate a signing identity. The
caller must verify the OS signature before invoking this tool, then run the
ordinary package verifier. Keep installer-only links outside the staged tree.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile


def refresh(root: Path, signing: str) -> dict:
    root = Path(root).resolve()
    manifest = root / 'package-manifest.json'
    value = json.loads(manifest.read_text(encoding='utf-8'))
    if value.get('schema') != 1 or value.get('platform') not in {'macos', 'windows'}:
        raise ValueError('Unsupported existing package manifest')
    for key in ['version', 'target', 'dependency_lock_sha256']:
        if not isinstance(value.get(key), str) or not value[key]:
            raise ValueError(f'Missing original build provenance: {key}')
    if not signing.strip():
        raise ValueError('The signing description must not be empty')
    members = sorted(root.rglob('*'))
    if any(path.is_symlink() for path in members):
        raise ValueError('Symbolic links are not allowed in the verified staged package')
    relative = ('Insect Realism.app/Contents/MacOS/InsectRealism'
                if value['platform'] == 'macos' else 'InsectRealism.exe')
    binary = root / relative
    data = binary.read_bytes()
    if value['platform'] == 'macos':
        if data[:4] not in {b'\xcf\xfa\xed\xfe', b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca'}:
            raise ValueError('Signed payload is not a supported Mach-O binary')
    elif len(data) < 256 or data[:2] != b'MZ':
        raise ValueError('Signed payload is not a PE binary')
    records = []
    for path in members:
        if path.is_file() and path != manifest:
            payload = path.read_bytes()
            records.append({'path': path.relative_to(root).as_posix(),
                            'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()})
    value.update(files=records, binary_sha256=hashlib.sha256(data).hexdigest(), signing=signing)
    encoded = (json.dumps(value, indent=2, sort_keys=True) + '\n').encode('utf-8')
    temporary = None
    try:
        with tempfile.NamedTempFile(dir=root, prefix='.manifest-', delete=False) as output:
            temporary = Path(output.name)
            output.write(encoded)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, manifest)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--signing', required=True)
    args = parser.parse_args()
    try:
        result = refresh(args.root, args.signing)
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f'Manifest refresh failed: {error}\n')
    print(json.dumps({'files': len(result['files']), 'binary_sha256': result['binary_sha256'],
                      'native_input_verified': result.get('native_input_verified', False)}))


if __name__ == '__main__':
    main()
