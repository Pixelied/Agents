from pathlib import Path

from insect_research.paths import TOP_LEVEL_DIRS, derived_db_path, initialize_pack


def test_initialize_pack_creates_canonical_tree(tmp_path: Path) -> None:
    initialize_pack(tmp_path)
    assert len(TOP_LEVEL_DIRS) == 13
    assert (tmp_path / "00_MASTER_INDEX").is_dir()
    assert (tmp_path / "08_DERIVED_BIOLOGY_DATABASE").is_dir()
    assert (tmp_path / "12_EXTERNAL_HUGE_DATASETS").is_dir()


def test_derived_db_path_uses_canonical_directory() -> None:
    assert str(derived_db_path("species.csv")).endswith(
        "INSECT_REALISM_MEGA_PACK/08_DERIVED_BIOLOGY_DATABASE/species.csv"
    )
