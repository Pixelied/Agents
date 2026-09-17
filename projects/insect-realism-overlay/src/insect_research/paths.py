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


def safe_relative_path(root: Path, name: str) -> Path:
    """Resolve an archive-relative POSIX path without accepting symlink traversal."""
    from pathlib import PurePosixPath
    if not isinstance(name, str) or not name or '\\' in name or '\x00' in name:
        raise ValueError('invalid archive-relative path')
    relative = PurePosixPath(name)
    if relative.is_absolute() or '..' in relative.parts or str(relative) == '.':
        raise ValueError('path must stay inside the archive')
    if str(relative) != name or ':' in relative.parts[0]:
        raise ValueError('path must be normalized and portable')
    candidate = root
    for part in relative.parts:
        candidate = candidate / part
        if candidate.is_symlink():
            raise ValueError('symlinks are not permitted in archive paths')
    try:
        candidate.resolve().relative_to(root.resolve())
    except ValueError as exc:
        raise ValueError('path escapes the archive') from exc
    return candidate
