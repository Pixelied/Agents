from dataclasses import asdict, dataclass
import json
from pathlib import Path


@dataclass(frozen=True, slots=True)
class DownloadEntry:
    source_id: str
    url: str
    destination: str
    expected_sha256: str
    expected_size_bytes: int
    bundled: bool

    def __post_init__(self) -> None:
        if not self.source_id:
            raise ValueError("source_id must not be empty")
        if self.expected_sha256 and len(self.expected_sha256) != 64:
            raise ValueError("expected_sha256 must be a 64-character SHA-256 digest")


def write_download_manifest(path: Path, entries: list[DownloadEntry]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = [asdict(entry) for entry in sorted(entries, key=lambda e: e.source_id)]
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
