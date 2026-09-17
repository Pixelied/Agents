"""The portable archive layout, independent of the package installation path."""
from pathlib import Path

ARCHIVE_ROOT = Path("INSECT_REALISM_MEGA_PACK")
TOP_LEVEL_DIRS = (
    "00_MASTER_INDEX", "01_ANTS", "02_RAW_ANT_DATASETS",
    "03_OTHER_TINY_CREATURES", "04_OPEN_SOURCE_SIMULATORS",
    "05_SOURCE_CODE_ANALYSIS", "06_VISUAL_ASSETS", "07_REAL_VIDEO_REFERENCE",
    "08_DERIVED_BIOLOGY_DATABASE", "09_REALISM_MODEL",
    "10_DESKTOP_OVERLAY_RESEARCH", "11_LICENSE_AND_ATTRIBUTION",
    "12_EXTERNAL_HUGE_DATASETS",
)


def derived_db_path(name: str) -> Path:
    """Return a table location; a table name is not an arbitrary filesystem path."""
    if (not isinstance(name, str) or not name or name != name.strip()
            or name in {".", ".."} or any(c in name for c in "/\\:\x00")
            or any(ord(c) < 32 for c in name)):
        raise ValueError("name must be a safe, non-empty table filename")
    return ARCHIVE_ROOT / "08_DERIVED_BIOLOGY_DATABASE" / name


def initialize_pack(root: Path = ARCHIVE_ROOT) -> None:
    """Create the thirteen directories without replacing existing evidence."""
    root = Path(root)
    targets = [root, *(root / name for name in TOP_LEVEL_DIRS)]
    # Preflight first so a bad existing target cannot redirect writes elsewhere.
    for path in targets:
        if path.is_symlink():
            raise ValueError(f"archive directory must not be a symlink: {path}")
        if path.exists() and not path.is_dir():
            raise ValueError(f"archive directory is a non-directory: {path}")
    for path in targets:
        path.mkdir(parents=True, exist_ok=True)


def validate_relative_path(value: str) -> None:
    """Require a portable, archive-root-relative POSIX path, not a URL."""
    if (not isinstance(value, str) or not value or value.startswith("/")
            or any(c in value for c in '\\:\x00<>"|?*')
            or any(ord(c) < 32 for c in value)):
        raise ValueError("path must be a safe relative POSIX path")
    reserved = {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}
    for part in value.split("/"):
        if (part in {"", ".", ".."} or part.endswith((" ", "."))
                or part.split(".")[0].upper() in reserved):
            raise ValueError("path contains a non-portable or unsafe component")
