from pathlib import Path
import os
import subprocess
import sys

import pytest

from insect_research.paths import ARCHIVE_ROOT, TOP_LEVEL_DIRS, derived_db_path, initialize_pack


def test_initialize_pack_creates_canonical_tree(tmp_path):
    initialize_pack(tmp_path)
    assert len(TOP_LEVEL_DIRS) == 13
    assert len(set(TOP_LEVEL_DIRS)) == 13
    assert {p.name for p in tmp_path.iterdir()} == set(TOP_LEVEL_DIRS)
    assert all((tmp_path / name).is_dir() for name in TOP_LEVEL_DIRS)


def test_initializer_preserves_existing_evidence(tmp_path):
    initialize_pack(tmp_path)
    note = tmp_path / "01_ANTS" / "review.md"
    note.write_text("evidence", encoding="utf-8")
    initialize_pack(tmp_path)
    assert note.read_text(encoding="utf-8") == "evidence"


def test_derived_db_path_uses_canonical_directory():
    assert derived_db_path("species.csv") == ARCHIVE_ROOT / "08_DERIVED_BIOLOGY_DATABASE" / "species.csv"


@pytest.mark.parametrize("name", ["", ".", "..", "../escape.csv", "/tmp/x.csv", "x/y.csv", "x\\y.csv", "C:bad.csv", "x\x00.csv"])
def test_derived_table_names_cannot_escape_archive(name):
    with pytest.raises(ValueError):
        derived_db_path(name)


def test_initializer_rejects_existing_symlink_directory(tmp_path):
    outside = tmp_path / "outside"
    outside.mkdir()
    root = tmp_path / "pack"
    root.mkdir()
    try:
        (root / "01_ANTS").symlink_to(outside, target_is_directory=True)
    except OSError:
        pytest.skip("symlink creation is not permitted on this host")
    with pytest.raises(ValueError, match="symlink"):
        initialize_pack(root)
    assert list(outside.iterdir()) == []


def test_init_cli_works_from_another_directory(tmp_path):
    project = Path(__file__).resolve().parents[1]
    env = dict(os.environ, PYTHONPATH=str(project / "src"))
    root = tmp_path / "new-pack"
    result = subprocess.run([sys.executable, str(project / "scripts/init_pack.py"), "--root", str(root)], cwd=tmp_path, env=env, text=True, capture_output=True)
    assert result.returncode == 0, result.stderr
    assert (root / "12_EXTERNAL_HUGE_DATASETS").is_dir()
