import hashlib
import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'import_checkpoint.py'


def git_sha(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


class ImportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        spec = importlib.util.spec_from_file_location('import_checkpoint', SCRIPT)
        if spec is None or spec.loader is None or not SCRIPT.is_file():
            self.fail('The verified checkpoint importer must exist')
        self.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.mod)
        self.data = b'checkpoint payload\n'
        self.manifest = self.root / 'manifest.txt'
        self.manifest.write_text(git_sha(self.data) + ' app/Cargo.toml\n', encoding='utf-8')
        self.archive = self.root / 'input.zip'
        self.out = self.root / 'out'

    def build_zip(self, name='insect-realism-desktop-app/app/Cargo.toml', data=None, mode=0o100644):
        with zipfile.ZipFile(self.archive, 'w') as z:
            entry = zipfile.ZipInfo(name)
            entry.external_attr = mode << 16
            z.writestr(entry, self.data if data is None else data)
            z.writestr('insect-realism-desktop-app/verification/large-log.txt', b'not source')
        return hashlib.sha256(self.archive.read_bytes()).hexdigest()

    def run_import(self, digest):
        return self.mod.stage_checkpoint(self.archive, self.manifest, self.out, digest)

    def test_imports_only_manifest_source(self):
        result = self.run_import(self.build_zip())
        self.assertEqual((self.out / 'app/Cargo.toml').read_bytes(), self.data)
        self.assertFalse((self.out / 'verification').exists())
        self.assertEqual(result['imported_files'], 1)
        self.assertFalse(result['native_build_verified'])

    def test_preserves_executable_mode(self):
        self.run_import(self.build_zip(mode=0o100755))
        self.assertTrue((self.out / 'app/Cargo.toml').stat().st_mode & stat.S_IXUSR)

    def test_rejects_archive_checksum_mismatch_without_output(self):
        self.build_zip()
        with self.assertRaises(ValueError):
            self.run_import('0' * 64)
        self.assertFalse(self.out.exists())

    def test_rejects_source_blob_mismatch_without_output(self):
        digest = self.build_zip(data=b'wrong contents')
        with self.assertRaises(ValueError):
            self.run_import(digest)
        self.assertFalse(self.out.exists())

    def test_rejects_missing_source_without_output(self):
        digest = self.build_zip(name='insect-realism-desktop-app/other.rs')
        with self.assertRaises(ValueError):
            self.run_import(digest)
        self.assertFalse(self.out.exists())

    def test_rejects_traversal_in_manifest(self):
        self.manifest.write_text(git_sha(self.data) + ' ../outside\n')
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip())
        self.assertFalse((self.root / 'outside').exists())

    def test_rejects_manifest_backslash(self):
        self.manifest.write_text(git_sha(self.data) + ' app\\Cargo.toml\n')
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip())

    def test_rejects_symlink_payload(self):
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip(mode=0o120777))
        self.assertFalse(self.out.exists())

    def test_refuses_overwriting_existing_destination(self):
        self.out.mkdir()
        sentinel = self.out / 'keep.txt'
        sentinel.write_text('preserve')
        with self.assertRaises(FileExistsError):
            self.run_import(self.build_zip())
        self.assertEqual(sentinel.read_text(), 'preserve')

    def test_rejects_duplicate_manifest_paths(self):
        line = self.manifest.read_text()
        self.manifest.write_text(line + line)
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip())

    def test_rejects_font_files(self):
        self.manifest.write_text(git_sha(self.data) + ' font.ttf\n')
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip(name='insect-realism-desktop-app/font.ttf'))

    def test_rejects_git_metadata(self):
        self.manifest.write_text(git_sha(self.data) + ' .git/config\n')
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip(name='insect-realism-desktop-app/.git/config'))

    def test_rejects_invalid_hash(self):
        self.manifest.write_text('not-a-hash app/Cargo.toml\n')
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip())

    def test_rejects_empty_manifest(self):
        self.manifest.write_text('')
        with self.assertRaises(ValueError):
            self.run_import(self.build_zip())


if __name__ == '__main__':
    unittest.main()
