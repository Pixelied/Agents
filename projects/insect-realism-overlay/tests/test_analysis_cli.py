import json
import os
from pathlib import Path
import subprocess
import sys

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / 'scripts' / 'analyze_trajectory.py'


def run_cli(tmp_path, *, extra=(), contents='track_id,time_s,x_mm,y_mm\na,0,0,0\na,1,1,0\na,2,1,0\na,3,2,0\n'):
    source = tmp_path / 'tracks.csv'
    source.write_text(contents)
    output = tmp_path / 'result'
    env = dict(os.environ, PYTHONPATH=str(ROOT / 'src'))
    args = [sys.executable, str(CLI), '--input', str(source), '--source-id', 'synthetic-test-only',
            '--species-id', 'synthetic', '--behavior-state', 'test',
            '--calibration-notes', 'Synthetic coordinates; not biological evidence',
            '--stop-threshold-mm-s', '0', '--output-dir', str(output), *extra]
    return subprocess.run(args, capture_output=True, text=True, env=env), output


def test_cli_exports_provenance_units_and_censoring(tmp_path):
    result, out = run_cli(tmp_path)
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload['source_id'] == 'synthetic-test-only'
    assert len(payload['input_sha256']) == 64
    assert payload['summary']['observed_interval_count'] == 3
    assert payload['stops']['complete_bout_count'] == 1
    for name in ('walking_speed.csv', 'acceleration.csv', 'turn_parameters.csv', 'stop_durations.csv'):
        rows = pd.read_csv(out / name)
        assert rows.iloc[0]['source_ids'] == 'synthetic-test-only'
        assert rows.iloc[0]['species_id'] == 'synthetic'
        assert rows.iloc[0]['unit']
    assert (out / 'normalized_tracks.csv').is_file()
    assert (out / 'stop_bouts.csv').is_file()
    assert json.loads((out / 'analysis.json').read_text()) == payload


def test_cli_emits_strict_json_for_unobservable_metrics(tmp_path):
    result, out = run_cli(tmp_path, contents='track_id,time_s,x_mm,y_mm\na,0,0,0\n')
    assert result.returncode == 0, result.stderr
    assert 'NaN' not in result.stdout and 'Infinity' not in result.stdout
    assert json.loads(result.stdout)['summary']['median_speed_mm_s'] is None
    assert pd.read_csv(out / 'walking_speed.csv').iloc[0]['sample_count'] == 0


def test_cli_refuses_overwrite_unrelated_existing_outputs(tmp_path):
    first, out = run_cli(tmp_path)
    assert first.returncode == 0, first.stderr
    (out / 'sentinel.txt').write_text('preserve')
    second, _ = run_cli(tmp_path)
    assert second.returncode != 0
    assert 'exists' in second.stderr
    assert (out / 'sentinel.txt').read_text() == 'preserve'


def test_cli_rejects_mixed_species_in_single_population_export(tmp_path):
    result, out = run_cli(tmp_path, contents='track_id,time_s,x_mm,y_mm,species_id\na,0,0,0,species-a\na,1,1,0,species-b\n')
    assert result.returncode != 0
    assert not out.exists()
    assert 'species' in result.stderr.lower()
