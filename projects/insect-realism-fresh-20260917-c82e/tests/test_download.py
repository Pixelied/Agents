"""Real loopback HTTP responses exercise transport, Range and file publication."""
from contextlib import contextmanager
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import socket
from threading import Thread
import pytest

from insect_research.download import download_with_resume

DATA = b'ants' * 1024
DIGEST = sha256(DATA).hexdigest()


@contextmanager
def server(*, interrupt=False, ignore_range=False, bad_range=False, new_etag=False, wrong_size=False, no_etag=False):
    calls = []
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'
        def log_message(self, *args):
            pass
        def do_GET(self):
            headers = dict(self.headers)
            calls.append(headers)
            if self.path == '/not-found':
                self.send_error(404); return
            start = int(self.headers.get('Range', 'bytes=0-')[6:-1])
            ranged = bool(self.headers.get('Range')) and not ignore_range
            if ranged and start >= len(DATA):
                self.send_response(416); self.send_header('Content-Length', '0'); self.end_headers(); return
            if not ranged:
                start = 0
            self.send_response(206 if ranged else 200)
            if not no_etag:
                self.send_header('ETag', '"v2"' if new_etag and ranged else '"v1"')
            self.send_header('Content-Length', str(len(DATA) - start + (1 if wrong_size else 0)))
            if ranged:
                self.send_header('Content-Range', f'bytes {start + (1 if bad_range else 0)}-{len(DATA)-1}/{len(DATA)}')
            self.end_headers()
            body = DATA[start:]
            if interrupt and len(calls) == 1:
                self.wfile.write(body[:len(body)//2]); self.wfile.flush()
                self.connection.shutdown(socket.SHUT_RDWR); self.connection.close(); return
            self.wfile.write(body)
            if wrong_size:
                self.wfile.flush(); self.connection.shutdown(socket.SHUT_RDWR); self.connection.close()
    httpd = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = Thread(target=httpd.serve_forever, daemon=True); thread.start()
    try:
        yield f'http://127.0.0.1:{httpd.server_port}/data', calls
    finally:
        httpd.shutdown(); httpd.server_close(); thread.join(timeout=2)


@pytest.mark.parametrize('url', ['ftp://example.org/x', 'file:///x', 'https://', 'https://u:p@example.org/x'])
def test_download_rejects_nonpublic_url_syntax(tmp_path, url):
    with pytest.raises(ValueError, match='HTTP'):
        download_with_resume(url, tmp_path / 'x')


@pytest.mark.parametrize('size,digest', [(-1, ''), (True, ''), (0, 'z' * 64)])
def test_download_validates_manifest_before_creating_files(tmp_path, size, digest):
    with pytest.raises(ValueError):
        download_with_resume('https://example.org/x', tmp_path / 'x', size, digest)
    assert list(tmp_path.iterdir()) == []


def test_download_publishes_only_verified_file_and_uses_cache(tmp_path):
    target = tmp_path / 'raw.csv'
    with server() as (url, calls):
        assert download_with_resume(url, target, len(DATA), DIGEST) == target
        assert download_with_resume(url, target, len(DATA), DIGEST) == target
    assert target.read_bytes() == DATA
    assert len(calls) == 1
    assert sorted(p.name for p in tmp_path.iterdir()) == ['raw.csv']


@pytest.mark.parametrize('ignore_range', [False, True])
def test_interrupted_download_resumes_or_restarts_without_duplicate_bytes(tmp_path, ignore_range):
    target = tmp_path / 'raw.csv'
    with server(interrupt=True, ignore_range=ignore_range) as (url, calls):
        with pytest.raises((OSError, ValueError)):
            download_with_resume(url, target, len(DATA), DIGEST)
        assert not target.exists()
        assert 0 < target.with_suffix('.csv.part').stat().st_size < len(DATA)
        download_with_resume(url, target, len(DATA), DIGEST)
    assert target.read_bytes() == DATA
    assert calls[1]['Range'] == f'bytes={len(DATA)//2}-'
    assert calls[1]['If-Range'] == '"v1"'


@pytest.mark.parametrize('bad_range,new_etag', [(True, False), (False, True)])
def test_bad_resume_response_never_appends_or_publishes(tmp_path, bad_range, new_etag):
    target = tmp_path / 'raw.csv'
    with server(interrupt=True, bad_range=bad_range, new_etag=new_etag) as (url, calls):
        with pytest.raises((OSError, ValueError)):
            download_with_resume(url, target, len(DATA), DIGEST)
        partial = target.with_suffix('.csv.part'); before = partial.read_bytes()
        with pytest.raises(ValueError, match='[Rr]ange|ETag'):
            download_with_resume(url, target, len(DATA), DIGEST)
        assert partial.read_bytes() == before
        assert not target.exists()


def test_unbound_partial_is_restarted_not_blindly_appended(tmp_path):
    target = tmp_path / 'x'; target.with_suffix('.part').write_bytes(b'wrong source')
    with server() as (url, calls):
        download_with_resume(url, target, len(DATA), DIGEST)
    assert 'Range' not in calls[0]
    assert target.read_bytes() == DATA


def test_checksum_failure_preserves_old_destination_and_discards_corrupt_partial(tmp_path):
    target = tmp_path / 'x'; target.write_bytes(b'previous valid file')
    with server() as (url, _):
        with pytest.raises(ValueError, match='SHA-256'):
            download_with_resume(url, target, len(DATA), '0' * 64)
    assert target.read_bytes() == b'previous valid file'
    assert not target.with_suffix('.part').exists()
    assert not target.with_suffix('.part.json').exists()


def test_short_body_without_expected_size_is_detected(tmp_path):
    with server(wrong_size=True) as (url, _):
        with pytest.raises((ValueError, OSError)):
            download_with_resume(url, tmp_path / 'x')
    assert not (tmp_path / 'x').exists()


def test_complete_verified_partial_is_promoted_without_request(tmp_path):
    target = tmp_path / 'x'; target.with_suffix('.part').write_bytes(DATA)
    with server() as (url, calls):
        download_with_resume(url, target, len(DATA), DIGEST)
    assert target.read_bytes() == DATA
    assert calls == []


@pytest.mark.parametrize('which', ['destination', 'partial', 'metadata', 'parent'])
def test_symlink_paths_are_rejected_before_network(tmp_path, which):
    outside = tmp_path / 'outside'; outside.mkdir(); external = outside / 'file'; external.write_bytes(b'untouched')
    root = tmp_path / 'root'; root.mkdir(); target = root / 'x'
    try:
        if which == 'parent':
            target = root / 'link' / 'x'; (root / 'link').symlink_to(outside, target_is_directory=True)
        else:
            link = {'destination': target, 'partial': target.with_suffix('.part'), 'metadata': target.with_suffix('.part.json')}[which]
            link.symlink_to(external)
    except OSError:
        pytest.skip('symlinks unavailable on host')
    with pytest.raises(ValueError, match='symlink'):
        download_with_resume('https://example.org/x', target)
    assert external.read_bytes() == b'untouched'


def test_404_does_not_replace_destination(tmp_path):
    target = tmp_path / 'x'; target.write_bytes(b'keep')
    with server() as (url, _):
        with pytest.raises(OSError):
            download_with_resume(url.replace('/data', '/not-found'), target)
    assert target.read_bytes() == b'keep'


def test_download_lock_is_not_stolen(tmp_path):
    target = tmp_path / 'x'; target.with_suffix('.lock').write_text('other worker')
    with pytest.raises(RuntimeError, match='lock'):
        download_with_resume('https://example.org/x', target)
    assert target.with_suffix('.lock').read_text() == 'other worker'
