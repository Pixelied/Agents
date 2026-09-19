#!/usr/bin/env python3
"""Recreate the exact Continuation 02 build inputs from authenticated original data.

This is an offline build-time recovery tool, never application runtime code.
The original parser and sampling rules are preserved; all output byte digests
must match the existing checkpoint before any output is published.
"""
from __future__ import annotations
import argparse
import hashlib
import gzip
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile

SOURCE_SHA256 = '810c67bfcc2c078a01c3af8657d688c804027eb661c2b614a6a74c4b0d92da31'
METADATA_SHA256 = '28e597005616c9a95ce7aa5e82f3951cc6536284a3c2e7449920cbfcf467a5e1'
OUTPUTS = {
    'build-input.json': 'b5cc912378a92c8b9f838047c7a2fef6d7a115c1b9520a4c350d477fea319375',
    'runtime-profiles.bin': 'd58754a7064a10bc87cf415462dfe1ee5a48b528962fae3852ee0d3dd1dba07e',
    'runtime-profiles.report.json': '45569198db7df759b8bb036f6f3e4798ef0b61b0046286b11a824a29e2306ebf',
}


def verify_digest(path: Path, expected: str) -> None:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    if digest.hexdigest() != expected:
        raise ValueError('checkpoint checksum mismatch: ' + str(path))


def read_metadata(path: Path) -> dict:
    raw = path.read_bytes()
    if path.suffix == ".gz":
        raw = gzip.decompress(raw)
    if hashlib.sha256(raw).hexdigest() != METADATA_SHA256:
        raise ValueError("checkpoint metadata checksum mismatch")
    return json.loads(raw)


def read_tracks(path: Path) -> list[dict]:
    # The original normalization used pandas 2.2.3's high-precision CSV parser.
    # Python float() / round_trip changes some values by one ULP. Do not replace
    # this with "equivalent" parsing or round coordinates to make hashes pass.
    import pandas as pd
    if pd.__version__ != '2.2.3':
        raise ValueError('exact recovery requires pandas 2.2.3')
    data = pd.read_csv(path, float_precision='high', dtype={'ID': str})
    required = {'Frame', 'LeaderX', 'LeaderY', 'ID'}
    if not required.issubset(data.columns):
        raise ValueError('source columns are incomplete')
    if data.empty or data[list(required)].isna().any().any():
        raise ValueError('source must contain finite, identified observations')
    for column in ('Frame', 'LeaderX', 'LeaderY'):
        if not data[column].map(lambda x: isinstance(x, (int, float)) and math.isfinite(x)).all():
            raise ValueError('source observations must be finite numeric values')
    if ((data.Frame < 0) | (data.Frame % 1 != 0)).any():
        raise ValueError('source frame numbers must be nonnegative integers')
    selected = data[(data.Frame % 10 == 0) & (data.Frame / 29.97 < 900)]
    if selected.duplicated(['ID', 'Frame']).any():
        raise ValueError('duplicate source track/frame')
    tracks = []
    for identity, group in selected.groupby('ID', sort=True):
        points = [[float(row.Frame), float(row.Frame) / 29.97, float(row.LeaderX), float(row.LeaderY)]
                  for row in group.sort_values('Frame', kind='stable').itertuples(index=False)]
        tracks.append({'track_id': identity + ':leader', 'species': 'Temnothorax rugatulus', 'points': points})
    return tracks


def publish_identical(stage: Path, output: Path, names: list[str]) -> None:
    # Check every destination first. A conflicting later file must not permit
    # partial replacement of earlier files from a newer source revision.
    for name in names:
        if Path(name).name != name or name in ('.', '..'):
            raise ValueError('invalid output basename')
        source, target = stage / name, output / name
        if source.is_symlink() or target.is_symlink():
            raise ValueError('symlink output is not supported')
        if target.exists() and (not target.is_file() or target.read_bytes() != source.read_bytes()):
            raise ValueError('existing output differs; preserve newer work: ' + str(target))
    output.mkdir(parents=True, exist_ok=True)
    for name in names:
        target = output / name
        if not target.exists():
            # Same-filesystem hard link publishes atomically without replacing a
            # concurrently created destination; deleting the temporary stage is safe.
            os.link(stage / name, target)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--coordinates', type=Path, required=True)
    parser.add_argument('--compiler', type=Path, required=True)
    parser.add_argument('--metadata', type=Path, default=Path(__file__).with_name('profile-metadata.json.gz'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    verify_digest(args.coordinates, SOURCE_SHA256)
    envelope = read_metadata(args.metadata)
    if envelope['input']['tracks']:
        raise ValueError('recovery metadata must not substitute trajectory observations')
    tracks = read_tracks(args.coordinates)
    if len(tracks) != 20 or sum(len(t['points']) for t in tracks) != 53960:
        raise ValueError('original track count or sampled observation count changed')
    envelope['input']['tracks'] = tracks
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.profile-recovery-', dir=output.parent) as directory:
        stage = Path(directory)
        candidate = stage / 'candidate.json'
        candidate.write_text(json.dumps(envelope, ensure_ascii=False, separators=(',', ':'), allow_nan=False), encoding='utf-8')
        # The unchanged Rust compiler verifies the original envelope digest and
        # emits its canonical serializer output. There is no digest bypass.
        subprocess.run([str(args.compiler.resolve()), 'compile', '--input', str(candidate),
                        '--output', str(stage / 'runtime-profiles.bin'),
                        '--report', str(stage / 'runtime-profiles.report.json'),
                        '--export-input', str(stage / 'build-input.json')], check=True)
        for name, digest in OUTPUTS.items():
            verify_digest(stage / name, digest)
        publish_identical(stage, output, list(OUTPUTS))
    print('Exact checkpoint restored: 20 leader tracks, 53960 observations, three matching file digests.')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
