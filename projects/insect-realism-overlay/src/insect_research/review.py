"""Count documented review coverage, never bibliographic page extent.

This validates review records, not whether a human genuinely read the source.
Evidence quality still requires a reviewer checkpoint; no counter proves that.
"""
from datetime import date
from pathlib import Path

from insect_research.paths import safe_relative_path


def reviewed_depth(root: Path, entries: list[dict], source_ids: set[str]) -> tuple[int, list[tuple[str, str]]]:
    """Deduplicate paginated coverage and conservatively count web/code excerpts.

    Web equivalents use 500 reviewed words; code equivalents use 50 substantive
    reviewed lines. These are accounting conventions, not biological evidence.
    Inherited/unverified entries contribute zero, even when they claim a whole
    book was reviewed. Page numbers are 1-based and must identify exact pages.
    """
    issues = []
    seen_reviews = set()
    methods_by_source = {}
    for entry in entries:
        if isinstance(entry, dict) and entry.get('status') == 'verified':
            source_id = entry.get('source_id')
            method = entry.get('method')
            if isinstance(source_id, str) and isinstance(method, str):
                methods_by_source.setdefault(source_id, set()).add(method)
    mixed_sources = {sid for sid, methods in methods_by_source.items() if len(methods) > 1}
    issues.extend(('mixed-review-accounting', sid) for sid in sorted(mixed_sources))
    pages = set()
    excerpts = {}
    for entry in entries:
        label = str(entry.get('review_id', '')) if isinstance(entry, dict) else 'invalid entry'
        if not isinstance(entry, dict):
            issues.append(('invalid-review-entry', label))
            continue
        if label in seen_reviews:
            issues.append(('duplicate-review-id', label))
            continue
        seen_reviews.add(label)
        source_id = entry.get('source_id')
        if source_id not in source_ids:
            issues.append(('unknown-review-source', f'{label}: {source_id}'))
            continue
        if source_id in mixed_sources or entry.get('status') != 'verified':
            continue
        try:
            for field in ('review_id', 'reviewer', 'locator', 'evidence_path'):
                if not isinstance(entry.get(field), str) or not entry[field].strip():
                    raise ValueError(f'{field} is required')
            date.fromisoformat(entry['reviewed_on'])
            evidence = safe_relative_path(root, entry['evidence_path'])
            if not evidence.is_file() or not evidence.read_text(encoding='utf-8').strip():
                issues.append(('missing-review-evidence', label))
                continue
            count = entry.get('page_equivalent')
            if type(count) is not int or count < 0:
                raise ValueError('page_equivalent must be a non-negative integer')
            method = entry.get('method')
            if method == 'paginated':
                numbers = entry.get('page_numbers')
                if not isinstance(numbers, list) or not numbers:
                    raise ValueError('exact reviewed page_numbers are required')
                if any(type(n) is not int or n < 1 for n in numbers):
                    raise ValueError('page numbers must be positive integers')
                if count != len(set(numbers)):
                    raise ValueError('page_equivalent disagrees with unique reviewed pages')
                pages.update((source_id, n) for n in numbers)
            elif method in ('web-500-words', 'code-50-lines'):
                field, divisor = ('reviewed_word_count', 500) if method == 'web-500-words' else ('reviewed_line_count', 50)
                amount = entry.get(field)
                if type(amount) is not int or amount < 0 or count > amount // divisor:
                    raise ValueError('equivalent exceeds conservative reviewed-content basis')
                key = (source_id, entry['locator'].strip().casefold())
                excerpts[key] = min(excerpts.get(key, count), count)
            else:
                raise ValueError('unsupported review accounting method')
        except (KeyError, TypeError, ValueError, OSError) as exc:
            issues.append(('invalid-review-entry', f'{label}: {exc}'))
    return len(pages) + sum(excerpts.values()), issues
