from insect_research.manifests import DownloadEntry


def test_download_entry_requires_source_id() -> None:
    entry = DownloadEntry(
        source_id="dataset-ant-tracks",
        url="https://example.org/ant-tracks.zip",
        destination="02_RAW_ANT_DATASETS/trajectories/ant-tracks.zip",
        expected_sha256="a" * 64,
        expected_size_bytes=123,
        bundled=False,
    )
    assert entry.bundled is False
