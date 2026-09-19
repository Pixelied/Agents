"""Release assets must carry hashes for the exact uploaded bytes, not ambiguous paths."""
from pathlib import Path
import hashlib
import importlib.util
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def implementation():
    path = ROOT / 'scripts/release_checksums.py'
    # An absent implementation is the specific failing behavior before this feature exists.
    if not path.exists():
        return None
    spec = importlib.util.spec_from_file_location('release_checksums', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseChecksumTests(unittest.TestCase):
    def helper(self):
        module = implementation()
        self.assertIsNotNone(module, 'A checksum writer must accompany the draft-release upload')
        return module

    def test_uploaded_basenames_have_exact_digests_in_deterministic_order(self):
        module = self.helper()
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'mac').mkdir()
            (root / 'win').mkdir()
            a, b = root / 'mac/a.zip', root / 'win/b.zip'
            a.write_bytes(b'Test asset A, not a native package')
            b.write_bytes(b'Test asset B, not a native package')
            output = root / 'SHA256SUMS.txt'
            module.write_checksums([b, a], output)
            first = output.read_bytes()
            self.assertEqual(first.decode(), ''.join(
                hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in [a, b]))
            module.write_checksums([a, b], output)
            self.assertEqual(first, output.read_bytes())

    def test_duplicate_upload_names_fail_instead_of_producing_ambiguous_hashes(self):
        module = self.helper()
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'one').mkdir(); (root / 'two').mkdir()
            a, b = root / 'one/app.zip', root / 'two/app.zip'
            a.write_bytes(b'A'); b.write_bytes(b'B')
            output = root / 'SHA256SUMS.txt'
            with self.assertRaisesRegex(ValueError, 'duplicate'):
                module.write_checksums([a, b], output)
            self.assertFalse(output.exists())

    def test_output_cannot_overwrite_an_asset(self):
        module = self.helper()
        with tempfile.TemporaryDirectory() as name:
            asset = Path(name) / 'asset.zip'
            asset.write_bytes(b'preserve these bytes')
            with self.assertRaisesRegex(ValueError, 'asset'):
                module.write_checksums([asset], asset)
            self.assertEqual(asset.read_bytes(), b'preserve these bytes')

    def test_empty_release_cannot_get_an_empty_success_manifest(self):
        module = self.helper()
        with tempfile.TemporaryDirectory() as name:
            with self.assertRaisesRegex(ValueError, 'asset'):
                module.write_checksums([], Path(name) / 'SHA256SUMS.txt')

    def test_workflow_attaches_the_combined_checksum_asset(self):
        workflow = (ROOT / '.github/workflows/insect-release.yml').read_text()
        draft = workflow.split('  draft-release:', 1)[1]
        self.assertIn('scripts/release_checksums.py', draft)
        self.assertIn('"${FILES[@]}" SHA256SUMS.txt --verify-tag', draft)


if __name__ == '__main__':
    unittest.main()
