from pathlib import Path
import pytest

from insect_research.download import download_with_resume


def test_download_rejects_non_http_url(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="HTTP"):
        download_with_resume("ftp://example.org/file.zip", tmp_path / "file.zip")
