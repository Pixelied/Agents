import csv
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def test_review_cli_does_not_certify_inherited_page_claims(tmp_path):
    master = tmp_path / '00_MASTER_INDEX'
    master.mkdir()
    with (master / 'SOURCE_CATALOG.csv').open('w', newline='') as f:
        csv.writer(f).writerows([['source_id', 'page_equivalent'], ['book-a', '2343']])
    (master / 'REVIEW_LEDGER.json').write_text('[]\n')
    result = subprocess.run([sys.executable, str(ROOT / 'scripts/verify_review_depth.py'), str(tmp_path)],
                            capture_output=True, text=True)
    assert result.returncode == 1, result.stderr
    payload = json.loads(result.stdout)
    assert payload['catalog_reported_page_equivalent'] == 2343
    assert payload['documented_reviewed_page_equivalent'] == 0
    assert payload['depth_gate_passed'] is False
    assert 'research-depth-below-target' in [i['code'] for i in payload['issues']]


def test_missing_review_ledger_is_a_controlled_failure(tmp_path):
    master = tmp_path / '00_MASTER_INDEX'
    master.mkdir()
    (master / 'SOURCE_CATALOG.csv').write_text('source_id,page_equivalent\nbook-a,2343\n')
    result = subprocess.run([sys.executable, str(ROOT / 'scripts/verify_review_depth.py'), str(tmp_path)],
                            capture_output=True, text=True)
    assert result.returncode == 1, result.stderr
    assert 'missing-review-ledger' in [i['code'] for i in json.loads(result.stdout)['issues']]
