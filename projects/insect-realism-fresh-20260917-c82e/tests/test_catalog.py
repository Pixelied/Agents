import csv
from dataclasses import replace
from datetime import date
import os
from pathlib import Path
import subprocess
import sys

import pytest
from insect_research.catalog import append_source, read_sources, catalog_page_equivalent
from insect_research.schema import LicenseClass, SourceKind, SourceRecord, SOURCE_FIELDS


def record(source_id="paper-a", stable_identifier="10.1234/example", **overrides):
    values = dict(source_id=source_id, title='Ant gait, "measured"', kind=SourceKind.PAPER,
                  canonical_url=f"https://example.org/{source_id}", stable_identifier=stable_identifier,
                  retrieved_on=date(2026, 9, 17), license_name="Publisher terms",
                  license_class=LicenseClass.REFERENCE_ONLY, page_equivalent=7,
                  notes_path=f"research/source_notes/{source_id}.md")
    values.update(overrides)
    return SourceRecord(**values)


def test_round_trip_and_deterministic_source_order(tmp_path):
    path = tmp_path / "sources.csv"
    a, b = record(), record("paper-b", "10.1234/second")
    append_source(path, b)
    append_source(path, a)
    assert read_sources(path) == [a, b]
    other = tmp_path / "other.csv"
    append_source(other, a)
    append_source(other, b)
    assert path.read_bytes() == other.read_bytes()


@pytest.mark.parametrize("second,match", [
    (record(), "duplicate source_id"),
    (record("paper-b", "10.1234/example"), "duplicate stable_identifier"),
    (record("paper-b", "doi:10.1234/EXAMPLE"), "duplicate stable_identifier"),
    (record("paper-b", "https://doi.org/10.1234/EXAMPLE"), "duplicate stable_identifier"),
    (record("paper-b", "another-id", canonical_url="https://EXAMPLE.org/paper-a#figure1"), "duplicate canonical_url"),
])
def test_duplicate_sources_cannot_enter_catalog(tmp_path, second, match):
    path = tmp_path / "sources.csv"
    append_source(path, record())
    before = path.read_bytes()
    with pytest.raises(ValueError, match=match):
        append_source(path, second)
    assert path.read_bytes() == before


def test_reviewed_depth_does_not_accept_double_counting():
    assert catalog_page_equivalent([record(), record("paper-b", "other", page_equivalent=3)]) == 10
    assert catalog_page_equivalent([]) == 0
    with pytest.raises(ValueError, match="duplicate"):
        catalog_page_equivalent([record(), record()])


def test_missing_catalog_is_empty_but_existing_empty_file_is_invalid(tmp_path):
    path = tmp_path / "sources.csv"
    assert read_sources(path) == []
    path.write_text("", encoding="utf-8")
    with pytest.raises(ValueError, match="header"):
        read_sources(path)


@pytest.mark.parametrize("text", [
    "source_id,title\na,b\n", ",".join(SOURCE_FIELDS) + "\na,b\n",
    ",".join(SOURCE_FIELDS) + '\n"unterminated\n',
])
def test_malformed_csv_is_rejected(tmp_path, text):
    path = tmp_path / "sources.csv"
    path.write_text(text, encoding="utf-8")
    with pytest.raises(ValueError):
        read_sources(path)


def test_existing_duplicate_rows_are_rejected_on_read(tmp_path):
    path = tmp_path / "sources.csv"
    append_source(path, record())
    with path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.reader(f))
    with path.open("a", newline="", encoding="utf-8") as f:
        csv.writer(f).writerow(rows[1])
    with pytest.raises(ValueError, match="duplicate"):
        read_sources(path)


def test_failed_atomic_replace_preserves_previous_catalog(tmp_path, monkeypatch):
    path = tmp_path / "sources.csv"
    append_source(path, record())
    before = path.read_bytes()
    import insect_research._io as storage
    def fail_replace(*args):
        raise OSError("simulated disk error")
    monkeypatch.setattr(storage.os, "replace", fail_replace)
    with pytest.raises(OSError, match="simulated"):
        append_source(path, record("paper-b", "other"))
    assert path.read_bytes() == before
    assert {p.name for p in tmp_path.iterdir()} == {"sources.csv"}


def test_busy_catalog_fails_closed_without_overwriting_lock(tmp_path):
    path = tmp_path / "sources.csv"
    lock = tmp_path / "sources.csv.lock"
    lock.write_text("another-writer", encoding="utf-8")
    with pytest.raises(RuntimeError, match="lock"):
        append_source(path, record())
    assert lock.read_text(encoding="utf-8") == "another-writer"
    assert not path.exists()


def test_intake_cli_requires_explicit_license_and_can_run_outside_project(tmp_path):
    project = Path(__file__).resolve().parents[1]
    env = dict(os.environ, PYTHONPATH=str(project / "src"))
    args = [sys.executable, str(project / "scripts/add_source.py"), "--root", str(tmp_path / "pack"),
            "--source-id", "paper-a", "--title", "Measured ant gait", "--kind", "paper",
            "--url", "https://example.org/gait", "--stable-identifier", "10.1234/gait",
            "--retrieved-on", "2026-09-17", "--license-name", "Publisher terms", "--page-equivalent", "0"]
    missing = subprocess.run(args, cwd=tmp_path, env=env, text=True, capture_output=True)
    assert missing.returncode == 2
    assert "--license-class" in missing.stderr
    success = subprocess.run([*args, "--license-class", "REFERENCE_ONLY"], cwd=tmp_path, env=env, text=True, capture_output=True)
    assert success.returncode == 0, success.stderr
    catalog = tmp_path / "pack/00_MASTER_INDEX/SOURCE_CATALOG.csv"
    assert read_sources(catalog)[0].license_class is LicenseClass.REFERENCE_ONLY
