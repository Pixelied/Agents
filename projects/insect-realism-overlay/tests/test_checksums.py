from pathlib import Path
from insect_research.checksums import sha256_file, verify_sha256


def test_sha256_file_and_verify(tmp_path: Path) -> None:
    path = tmp_path / "x.bin"
    path.write_bytes(b"ants")
    digest = sha256_file(path)
    assert digest == "b9ff6bf20e4c06505ffc06e1f4059838936938cc83f0d2d02adcdae945d8f984"
    assert verify_sha256(path, digest)
