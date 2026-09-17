"""Streaming SHA-256 checks independent of file size."""
import hashlib
import hmac
from pathlib import Path

from .schema import validate_sha256


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open('rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def verify_sha256(path: Path, expected: str) -> bool:
    validate_sha256(expected, allow_empty=False)
    return hmac.compare_digest(sha256_file(path), expected.lower())
