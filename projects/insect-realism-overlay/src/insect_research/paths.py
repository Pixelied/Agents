from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ARCHIVE_ROOT = REPO_ROOT / "INSECT_REALISM_MEGA_PACK"
TOP_LEVEL_DIRS = (
    "00_MASTER_INDEX",
    "01_ANTS",
    "02_RAW_ANT_DATASETS",
    "03_OTHER_TINY_CREATURES",
    "04_OPEN_SOURCE_SIMULATORS",
    "05_SOURCE_CODE_ANALYSIS",
    "06_VISUAL_ASSETS",
    "07_REAL_VIDEO_REFERENCE",
    "08_DERIVED_BIOLOGY_DATABASE",
    "09_REALISM_MODEL",
    "10_DESKTOP_OVERLAY_RESEARCH",
    "11_LICENSE_AND_ATTRIBUTION",
    "12_EXTERNAL_HUGE_DATASETS",
)


def derived_db_path(name: str) -> Path:
    return ARCHIVE_ROOT / "08_DERIVED_BIOLOGY_DATABASE" / name


def initialize_pack(root: Path = ARCHIVE_ROOT) -> None:
    for name in TOP_LEVEL_DIRS:
        (root / name).mkdir(parents=True, exist_ok=True)
