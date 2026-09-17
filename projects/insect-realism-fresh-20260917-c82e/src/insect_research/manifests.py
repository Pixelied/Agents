"""Canonical download metadata; inclusion is separate from permission review."""
from dataclasses import asdict, dataclass
import json
from pathlib import Path

from ._io import atomic_text_writer, exclusive_lock
from .paths import validate_relative_path
from .schema import validate_http_url, validate_sha256, validate_source_id


@dataclass(frozen=True, slots=True)
class DownloadEntry:
    source_id: str
    url: str
    destination: str
    expected_sha256: str
    expected_size_bytes: int
    bundled: bool

    def __post_init__(self) -> None:
        validate_source_id(self.source_id)
        validate_http_url(self.url)
        validate_relative_path(self.destination)
        if type(self.bundled) is not bool:
            raise ValueError('bundled must be a boolean')
        validate_sha256(self.expected_sha256, allow_empty=not self.bundled)
        if type(self.expected_size_bytes) is not int or self.expected_size_bytes < 0:
            raise ValueError('expected_size_bytes must be a non-negative integer')


def _validate_entries(entries: list[DownloadEntry]) -> None:
    seen: set[str] = set()
    for entry in entries:
        if not isinstance(entry, DownloadEntry):
            raise ValueError('manifest requires DownloadEntry records')
        key = entry.destination.casefold()
        if key in seen:
            raise ValueError(f'duplicate destination: {entry.destination}')
        seen.add(key)


def write_download_manifest(path: Path, entries: list[DownloadEntry]) -> None:
    _validate_entries(entries)
    payload = [asdict(entry) for entry in sorted(entries, key=lambda e: (e.source_id, e.destination, e.url))]
    with exclusive_lock(path), atomic_text_writer(path) as handle:
        handle.write(json.dumps(payload, indent=2, sort_keys=True, allow_nan=False) + '\n')


def read_download_manifest(path: Path) -> list[DownloadEntry]:
    if path.is_symlink():
        raise ValueError('manifest must not be a symlink')
    try:
        payload = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(payload, list):
            raise ValueError('manifest must be a JSON array')
        entries = [DownloadEntry(**item) for item in payload]
    except (TypeError, json.JSONDecodeError) as exc:
        raise ValueError(f'invalid download manifest: {path}') from exc
    _validate_entries(entries)
    return entries
