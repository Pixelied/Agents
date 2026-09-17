from pathlib import Path
import pytest
from insect_research.review import reviewed_depth


def section(**changes):
    row = {"source_id": "book-a", "review_id": "review-1", "status": "verified",
           "reviewer": "test-reviewer", "reviewed_on": "2026-09-17",
           "locator": "pages 1-3", "page_numbers": [1, 2, 3],
           "page_equivalent": 3, "method": "paginated", "evidence_path": "notes/a.md"}
    return row | changes


def evidence(root):
    (root / 'notes').mkdir()
    (root / 'notes/a.md').write_text('Test fixture only. Reviewed passages and extraction notes.')


def test_catalog_extent_is_not_reviewed_depth(tmp_path):
    total, issues = reviewed_depth(tmp_path, [], {'book-a'})
    assert total == 0


def test_overlapping_reviewed_pages_are_counted_once(tmp_path):
    evidence(tmp_path)
    rows = [section(), section(review_id='review-2', locator='pages 2-4', page_numbers=[2, 3, 4])]
    total, issues = reviewed_depth(tmp_path, rows, {'book-a'})
    assert total == 4
    assert not issues


def test_missing_evidence_cannot_contribute_depth(tmp_path):
    total, issues = reviewed_depth(tmp_path, [section()], {'book-a'})
    assert total == 0
    assert any(code == 'missing-review-evidence' for code, _ in issues)


def test_unknown_source_cannot_contribute_depth(tmp_path):
    evidence(tmp_path)
    total, issues = reviewed_depth(tmp_path, [section()], {'other'})
    assert total == 0
    assert any(code == 'unknown-review-source' for code, _ in issues)


def test_repeated_review_ids_are_rejected(tmp_path):
    evidence(tmp_path)
    total, issues = reviewed_depth(tmp_path, [section(), section()], {'book-a'})
    assert any(code == 'duplicate-review-id' for code, _ in issues)


def test_book_extent_without_identified_pages_is_rejected(tmp_path):
    evidence(tmp_path)
    total, issues = reviewed_depth(tmp_path, [section(page_numbers=[], page_equivalent=732)], {'book-a'})
    assert total == 0
    assert any(code == 'invalid-review-entry' for code, _ in issues)


def test_unverified_inherited_reviews_do_not_count(tmp_path):
    evidence(tmp_path)
    total, issues = reviewed_depth(tmp_path, [section(status='inherited-unverified')], {'book-a'})
    assert total == 0


def test_web_equivalents_need_conservative_word_basis_and_unique_locator(tmp_path):
    evidence(tmp_path)
    row = section(method='web-500-words', locator='methods calibration', page_numbers=[],
                  reviewed_word_count=600, page_equivalent=1)
    total, issues = reviewed_depth(tmp_path, [row, row | {'review_id': 'review-2'}], {'book-a'})
    assert total == 1
    assert not issues
    total, issues = reviewed_depth(tmp_path, [row | {'page_equivalent': 20}], {'book-a'})
    assert total == 0
    assert issues


@pytest.mark.parametrize('path', ['../outside.md', '/tmp/a.md', 'notes/../../outside.md', 'notes\\a.md'])
def test_review_evidence_cannot_escape_archive(tmp_path, path):
    evidence(tmp_path)
    total, issues = reviewed_depth(tmp_path, [section(evidence_path=path)], {'book-a'})
    assert total == 0
    assert issues


def test_mixed_counting_methods_cannot_double_count_one_source(tmp_path):
    evidence(tmp_path)
    second = section(review_id='web-copy', method='web-500-words',
                     locator='HTML copy of pages 1-3', page_numbers=[],
                     reviewed_word_count=1500, page_equivalent=3)
    total, issues = reviewed_depth(tmp_path, [section(), second], {'book-a'})
    assert total == 0
    assert any(code == 'mixed-review-accounting' for code, _ in issues)
