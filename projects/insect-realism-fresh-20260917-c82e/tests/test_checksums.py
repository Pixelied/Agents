from pathlib import Path
import hashlib
import pytest
from insect_research.checksums import sha256_file, verify_sha256


def test_streamed_known_sha256(tmp_path):
    path = tmp_path / 'ants.bin'
    path.write_bytes(b'ants')
    expected = 'b9ff6bf20e4c06505ffc06e1f4059838936938cc83f0d2d02adcdae945d8f984'
    assert sha256_file(path) == expected
    assert verify_sha256(path, expected.upper())
    assert not verify_sha256(path, '0' * 64)


def test_streams_multiple_chunks(tmp_path):
    path = tmp_path / 'large.bin'
    data = b'abcd' * (1024 * 1024)
    path.write_bytes(data)
    assert sha256_file(path) == hashlib.sha256(data).hexdigest()


@pytest.mark.parametrize('value', ['', 'x' * 64, 'a' * 63, None])
def test_verification_rejects_invalid_digest(tmp_path, value):
    path = tmp_path / 'a'; path.write_bytes(b'a')
    with pytest.raises(ValueError):
        verify_sha256(path, value)
