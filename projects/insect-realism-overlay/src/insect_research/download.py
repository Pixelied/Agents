from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from insect_research.checksums import verify_sha256


def download_with_resume(
    url: str,
    destination: Path,
    expected_size_bytes: int = 0,
    expected_sha256: str = "",
) -> Path:
    if urlparse(url).scheme not in {"http", "https"}:
        raise ValueError("downloads must use HTTP(S)")
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + ".part")
    offset = partial.stat().st_size if partial.exists() else 0
    headers = {"Range": f"bytes={offset}-"} if offset else {}
    request = Request(url, headers=headers)
    with urlopen(request) as response:
        status = getattr(response, "status", 200)
        mode = "ab" if offset and status == 206 else "wb"
        with partial.open(mode) as out:
            while chunk := response.read(1024 * 1024):
                out.write(chunk)
    if expected_size_bytes and partial.stat().st_size != expected_size_bytes:
        raise ValueError("download size does not match manifest")
    if expected_sha256 and not verify_sha256(partial, expected_sha256):
        raise ValueError("download SHA-256 does not match manifest")
    partial.replace(destination)
    return destination
