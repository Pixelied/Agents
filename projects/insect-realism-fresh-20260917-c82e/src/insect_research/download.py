"""Resumable HTTP(S) retrieval with atomic, validated publication.

Callers must verify permission before retrieval. A successful download is not a
license decision. Unverified bytes stay in .part files outside the final pack.
"""
import json
from pathlib import Path
import re
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from ._io import atomic_text_writer, exclusive_lock
from .checksums import verify_sha256
from .schema import validate_http_url, validate_sha256

_CHUNK = 1024 * 1024
_TIMEOUT_SECONDS = 30


def _reject_symlinks(path: Path) -> None:
    for part in (path, *path.parents):
        if part.is_symlink():
            raise ValueError(f'download paths must not contain a symlink: {part}')


def _length(value: str | None) -> int | None:
    if value is None:
        return None
    if not value.isascii() or not value.isdecimal():
        raise ValueError('invalid HTTP Content-Length')
    return int(value)


def download_with_resume(
    url: str, destination: Path, expected_size_bytes: int = 0, expected_sha256: str = '',
) -> Path:
    validate_http_url(url)
    validate_sha256(expected_sha256)
    if type(expected_size_bytes) is not int or expected_size_bytes < 0:
        raise ValueError('expected_size_bytes must be a non-negative integer')
    destination = Path(destination)
    partial = destination.with_suffix(destination.suffix + '.part')
    metadata = destination.with_suffix(destination.suffix + '.part.json')
    for path in (destination, partial, metadata):
        _reject_symlinks(path)
    expected_sha256 = expected_sha256.lower()
    with exclusive_lock(destination):
        def complete(path: Path) -> bool:
            return bool(expected_sha256 and path.is_file()
                        and (not expected_size_bytes or path.stat().st_size == expected_size_bytes)
                        and verify_sha256(path, expected_sha256))

        def discard_partial() -> None:
            partial.unlink(missing_ok=True)
            metadata.unlink(missing_ok=True)

        def publish() -> Path:
            partial.replace(destination)
            metadata.unlink(missing_ok=True)
            return destination

        if complete(destination):
            return destination
        if complete(partial):
            return publish()
        identity = dict(url=url, expected_size_bytes=expected_size_bytes, expected_sha256=expected_sha256)
        saved = {}
        if metadata.is_file():
            try:
                saved = json.loads(metadata.read_text(encoding='utf-8'))
                if not isinstance(saved, dict):
                    saved = {}
            except (ValueError, UnicodeError):
                saved = {}
        etag = saved.get('etag', '')
        strong_etag = isinstance(etag, str) and etag.startswith('"') and etag.endswith('"')
        bound = all(saved.get(key) == value for key, value in identity.items())
        offset = partial.stat().st_size if partial.is_file() and bound and (expected_sha256 or strong_etag) else 0
        if expected_size_bytes and offset >= expected_size_bytes:
            offset = 0  # A complete but wrong file must not enter an endless 416 loop.
        if not offset:
            discard_partial()
        headers = {'Accept-Encoding': 'identity', 'User-Agent': 'insect-realism-research/0.1'}
        if offset:
            headers['Range'] = f'bytes={offset}-'
            if strong_etag:
                headers['If-Range'] = etag
        request = Request(url, headers=headers)
        try:
            response = urlopen(request, timeout=_TIMEOUT_SECONDS)
        except HTTPError as exc:
            # Close error response sockets as well as successful ones. One safe
            # retry handles a remote file shortened since the interrupted request.
            code = exc.code
            exc.close()
            if code != 416 or not offset:
                raise
            discard_partial()
            offset = 0
            headers.pop('Range', None); headers.pop('If-Range', None)
            response = urlopen(Request(url, headers=headers), timeout=_TIMEOUT_SECONDS)
        with response:
            validate_http_url(response.geturl())
            if response.status not in {200, 206}:
                raise ValueError(f'unexpected HTTP response: {response.status}')
            if response.headers.get('Content-Encoding', 'identity').lower() not in {'identity', ''}:
                raise ValueError('HTTP encoded response cannot be safely resumed')
            length = _length(response.headers.get('Content-Length'))
            new_etag = response.headers.get('ETag', '')
            total = None
            if response.status == 206:
                match = re.fullmatch(r'bytes (\d+)-(\d+)/(\d+)', response.headers.get('Content-Range', ''))
                if not match:
                    raise ValueError('invalid Content-Range on partial response')
                start, end, total = map(int, match.groups())
                if start != offset or end < start or end >= total or (length is not None and length != end - start + 1):
                    raise ValueError('Content-Range does not match requested offset or length')
                if offset and strong_etag and new_etag != etag:
                    raise ValueError('ETag changed on a resumed partial response')
                if expected_size_bytes and total != expected_size_bytes:
                    raise ValueError('Content-Range total does not match manifest size')
            else:
                offset = 0  # Range ignored or If-Range entity changed: replace, never append.
                total = length
            if expected_size_bytes and total is not None and total != expected_size_bytes:
                raise ValueError('download size does not match manifest')
            with atomic_text_writer(metadata) as handle:
                handle.write(json.dumps({**identity, 'etag': new_etag}, sort_keys=True) + '\n')
            received = 0
            with partial.open('ab' if offset else 'wb') as handle:
                while chunk := response.read(_CHUNK):
                    handle.write(chunk)
                    received += len(chunk)
                    if expected_size_bytes and offset + received > expected_size_bytes:
                        raise ValueError('download exceeds manifest size')
            if length is not None and received != length:
                raise OSError('incomplete HTTP body; partial retained for a verified resume')
            size = partial.stat().st_size
            if total is not None and size != total:
                raise OSError('incomplete ranged download; partial retained')
        if expected_size_bytes and partial.stat().st_size != expected_size_bytes:
            raise ValueError('download size does not match manifest')
        if expected_sha256 and not verify_sha256(partial, expected_sha256):
            discard_partial()
            raise ValueError('download SHA-256 does not match manifest; corrupt partial discarded')
        return publish()
