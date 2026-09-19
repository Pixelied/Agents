#!/usr/bin/env python3
"""Audit actual Mega Pack secondary rows without assuming a different CSV schema."""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re

REASONS = {
    'booklice': ['No calibrated numerical ordinary-crawling trajectory model is supplied.',
                 'Shipping visual assets are not audited.'],
    'springtails': ['Jump mechanics do not establish an ordinary crawling profile.',
                    'Calibrated size is absent; a distinct jump/landing renderer is required.'],
    'tiny_beetles': ['Available mechanics primarily concern flight, not glass-surface walking.',
                    'Calibrated size and the shipping asset audit are absent.'],
    'gnats_fruit_flies': ['Treadmill kinematics do not establish a complete surface behavior profile.',
                         'Distinct leg/wing rendering and legal asset review remain unqualified.'],
    'spiderlings': ['Calibrated size and a complete crawling/gait profile are absent.',
                   'Eight-leg locomotion must not be implemented as an ant skin.'],
}
CANDIDATE_COLUMNS = {'group', 'candidate_species_or_taxon', 'source_ids', 'data_quality',
                     'asset_quality', 'implementation_complexity', 'recommended_after_ants'}
LICENSE_COLUMNS = {'source_id', 'license_name', 'license_class', 'shipping_allowed'}

def rows(path: Path, required: set[str]) -> list[dict[str, str]]:
    with path.open(newline='', encoding='utf-8-sig') as handle:
        reader = csv.DictReader(handle)
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f'{path.name}: missing columns: {", ".join(sorted(missing))}')
        data = list(reader)
    if any(None in row or any(row.get(key) is None for key in required) for row in data):
        raise ValueError(f'{path.name}: malformed row')
    return data

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def evaluate(pack: Path) -> dict:
    pack = Path(pack)
    candidates_path = pack / '03_OTHER_TINY_CREATURES/secondary_species.csv'
    licenses_path = pack / '00_MASTER_INDEX/LICENSE_MANIFEST.csv'
    licenses = {}
    for row in rows(licenses_path, LICENSE_COLUMNS):
        sid = row['source_id']
        if sid in licenses:
            raise ValueError('Duplicate license source: ' + sid)
        licenses[sid] = row
    candidates = []
    seen = set()
    for row in rows(candidates_path, CANDIDATE_COLUMNS):
        cid = row['group']
        if cid not in REASONS:
            raise ValueError('Unreviewed candidate requires explicit review: ' + cid)
        if cid in seen:
            raise ValueError('Duplicate candidate: ' + cid)
        seen.add(cid)
        ids = [s.strip() for s in re.split(r'[;|]', row['source_ids']) if s.strip()]
        if not ids or len(ids) != len(set(ids)):
            raise ValueError('Missing or duplicate source IDs: ' + cid)
        sources = []
        for sid in ids:
            if not re.fullmatch(r'[A-Za-z0-9_-]+', sid):
                raise ValueError('Invalid source identifier: ' + sid)
            record = licenses.get(sid)
            if record is None:
                raise ValueError('Missing license record: ' + sid)
            note = pack / '00_MASTER_INDEX/source_notes' / (sid + '.md')
            if not note.is_file():
                raise ValueError('Missing source note: ' + sid)
            sources.append({'source_id': sid, 'source_note': str(note.relative_to(pack)),
                            'source_note_sha256': digest(note), 'license_record': record})
        first = sources[0]
        candidates.append({
            'id': cid, 'species_context': row['candidate_species_or_taxon'],
            'qualified': False, 'reasons': REASONS[cid], 'original_row': row,
            'pack_recommended_after_ants': row['recommended_after_ants'],
            'evidence_quality': row['data_quality'], 'asset_quality': row['asset_quality'],
            'implementation_cost': row['implementation_complexity'], 'sources': sources,
            'source_id': first['source_id'], 'source_note': first['source_note'],
            'source_note_sha256': first['source_note_sha256'],
            'source_material_license': first['license_record']['license_name'],
            'source_material_shipping_allowed': first['license_record']['shipping_allowed'],
            'license_status': first['license_record']['license_class'],
        })
    if not candidates:
        raise ValueError('Empty candidate table is not a completed review')
    # These reasons describe an actual reviewed evidence snapshot, not a classifier.
    # New measurements or license terms must never inherit a stale decision silently.
    lock_path = Path(__file__).with_name('secondary_review_lock.json')
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    observed = {str(p.relative_to(pack)): digest(p)
                for p in [candidates_path, licenses_path]}
    for candidate in candidates:
        for source in candidate['sources']:
            observed[source['source_note']] = source['source_note_sha256']
    if lock.get('schema_version') != 1 or observed != lock.get('reviewed_inputs'):
        raise ValueError('Secondary evidence changed; an explicit new review is required')
    return {
        'schema_version': 2, 'decision': 'ants_only', 'shipping_creatures': ['ant'],
        'review_lock_sha256': digest(lock_path),
        'candidates': candidates,
        'input_sha256': {str(p.relative_to(pack)): digest(p)
                         for p in [candidates_path, licenses_path]},
        'qualification_boundary': 'Reference-only media are not redistributed. Scientific facts may inform '
        'independent implementation, but no supplied candidate passes the combined measurements, anatomy, '
        'behavior, reuse and ant-quality gate. A new or changed evidence base requires a fresh review.',
    }

def markdown(report: dict) -> str:
    text = ['# Secondary-creature qualification', '', '**Decision: ants only.**', '',
            report['qualification_boundary'], '',
            'All five source notes defer qualification pending independent size, locomotion and reuse '
            'evidence. Their original rows and license records are retained in the adjacent JSON.', '']
    for candidate in report['candidates']:
        text += ['## ' + candidate['id'] + ' - ' + candidate['species_context'], '',
                 'Not qualified: ' + ' '.join(candidate['reasons']),
                 'Evidence: ' + candidate['evidence_quality'] + '. Assets: ' + candidate['asset_quality'] + '.',
                 'Source: `' + candidate['source_id'] + '`; `' + candidate['source_note'] + '`.',
                 'Source-material classification: `' + candidate['license_status'] + '`; shipping allowed: `' +
                 candidate['source_material_shipping_allowed'] + '`.', '']
    text += ['## Reproduction', '',
             '`python scripts/qualify_secondary.py --pack /path/to/INSECT_REALISM_MEGA_PACK --output docs`', '',
             'Input-table and source-note SHA-256 digests are recorded in the JSON report.', '']
    return '\n'.join(text)

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    report = evaluate(args.pack)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'SECONDARY_CREATURE_GATE.json').write_text(
        json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    (args.output / 'SECONDARY_CREATURE_GATE.md').write_text(markdown(report), encoding='utf-8')
    print('Reviewed', len(report['candidates']), 'candidates; shipping profiles: ant only')

if __name__ == '__main__':
    main()
