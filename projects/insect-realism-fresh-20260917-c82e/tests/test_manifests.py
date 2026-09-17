from dataclasses import replace
import json
import pytest
from insect_research.manifests import DownloadEntry, read_download_manifest, write_download_manifest


def entry(**overrides):
    values = dict(source_id='dataset-a', url='https://example.org/a.csv',
                  destination='02_RAW_ANT_DATASETS/trajectories/a.csv', expected_sha256='a' * 64,
                  expected_size_bytes=123, bundled=False)
    values.update(overrides)
    return DownloadEntry(**values)


@pytest.mark.parametrize('overrides', [
    {'source_id': ''}, {'destination': '../outside'}, {'destination': '/outside'},
    {'destination': r'C:\outside'}, {'destination': 'raw/CON.csv'}, {'url': 'file:///x'},
    {'expected_sha256': 'x' * 64}, {'expected_size_bytes': -1}, {'expected_size_bytes': True},
    {'expected_size_bytes': 1.5}, {'bundled': 'false'}, {'bundled': True, 'expected_sha256': ''},
])
def test_manifest_entry_rejects_unsafe_or_ambiguous_metadata(overrides):
    with pytest.raises(ValueError):
        entry(**overrides)


def test_round_trip_is_deterministic_for_multiple_files_per_source(tmp_path):
    a, b = entry(), entry(destination='02_RAW_ANT_DATASETS/trajectories/b.csv', url='https://example.org/b.csv')
    first, second = tmp_path / 'a.json', tmp_path / 'b.json'
    write_download_manifest(first, [b, a]); write_download_manifest(second, [a, b])
    assert first.read_bytes() == second.read_bytes()
    assert read_download_manifest(first) == [a, b]


def test_duplicate_destinations_preserve_existing_manifest(tmp_path):
    path = tmp_path / 'manifest.json'; write_download_manifest(path, [entry()])
    original = path.read_bytes()
    with pytest.raises(ValueError, match='destination'):
        write_download_manifest(path, [entry(), entry(source_id='dataset-b')])
    assert path.read_bytes() == original


@pytest.mark.parametrize('payload', ['{}', '[{}]', '[{"extra":true}]', 'null', '['])
def test_reader_rejects_malformed_manifests(tmp_path, payload):
    path = tmp_path / 'manifest.json'; path.write_text(payload, encoding='utf-8')
    with pytest.raises(ValueError):
        read_download_manifest(path)


def test_empty_manifest_is_explicit_json_array(tmp_path):
    path = tmp_path / 'manifest.json'; write_download_manifest(path, [])
    assert path.read_text() == '[]\n'
    assert read_download_manifest(path) == []
