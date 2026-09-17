"""Audit documented review depth without treating book length as work performed.

This is an early Task 6 safeguard, not the complete Task 14 release auditor.
Passing this check alone NEVER certifies biology, licenses, or ZIP readiness.
"""
import argparse
import csv
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from insect_research.review import reviewed_depth


REQUIRED_REVIEWED_PAGES = 2000


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    args = parser.parse_args(argv)
    master = args.root / '00_MASTER_INDEX'
    issues = []
    reported = 0
    total = 0
    try:
        with (master / 'SOURCE_CATALOG.csv').open(newline='', encoding='utf-8') as stream:
            rows = list(csv.DictReader(stream))
        source_ids = [row['source_id'].strip() for row in rows]
        if not source_ids or any(not sid for sid in source_ids):
            raise ValueError('catalog requires non-empty source IDs')
        if len(source_ids) != len(set(source_ids)):
            raise ValueError('catalog contains duplicate source IDs')
        amounts = [int(row['page_equivalent']) for row in rows]
        if any(amount < 0 for amount in amounts):
            raise ValueError('reported page counts cannot be negative')
        reported = sum(amounts)
        ledger = master / 'REVIEW_LEDGER.json'
        if ledger.is_file():
            entries = json.loads(ledger.read_text(encoding='utf-8'))
            if not isinstance(entries, list):
                raise ValueError('review ledger must be a JSON array')
            total, review_issues = reviewed_depth(args.root, entries, set(source_ids))
            issues.extend({'code': code, 'message': message} for code, message in review_issues)
        else:
            issues.append({'code': 'missing-review-ledger', 'message': str(ledger)})
    except (OSError, ValueError, KeyError, TypeError) as exc:
        issues.append({'code': 'invalid-review-input', 'message': str(exc)})
    if total < REQUIRED_REVIEWED_PAGES:
        issues.append({'code': 'research-depth-below-target',
                       'message': f'{total} documented; {REQUIRED_REVIEWED_PAGES} required'})
    passed = not issues
    print(json.dumps({
        'catalog_reported_page_equivalent': reported,
        'documented_reviewed_page_equivalent': total,
        'required_reviewed_page_equivalent': REQUIRED_REVIEWED_PAGES,
        'depth_gate_passed': passed,
        'full_corpus_ready': False,
        'scope': 'review-depth safeguard only; full corpus acceptance remains separate',
        'issues': issues,
    }, indent=2, sort_keys=True))
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
