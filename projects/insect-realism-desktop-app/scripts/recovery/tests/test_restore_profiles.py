"""Recovery-only tests. Pinned pandas is a build-input reader, not an app dependency."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / 'restore_profiles.py'


class RestoreProfilesTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(MODULE.is_file(), 'exact profile recovery entrypoint is missing')
        spec = importlib.util.spec_from_file_location('restore_profiles', MODULE)
        self.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.mod)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def csv(self, rows):
        path = self.root / 'source.csv'
        path.write_text('Frame,LeaderX,LeaderY,FollowerX,FollowerY,Colony,Run,Day,ID\n' + rows, encoding='utf-8')
        return path

    def test_uses_original_high_precision_reader_not_python_float(self):
        path = self.csv('1060,95.969999999999999,337.16000000000003,1,2,A,A,1,A\n')
        got = self.mod.read_tracks(path)
        self.assertEqual(got[0]['points'], [[1060.0, 1060 / 29.97, 95.97000000000001, 337.16]])
        self.assertEqual(got[0]['track_id'], 'A:leader')
        self.assertEqual(got[0]['species'], 'Temnothorax rugatulus')

    def test_selects_original_phase_and_cutoff_without_resampling(self):
        path = self.csv('0,1,2,3,4,A,A,1,A\n1,9,9,9,9,A,A,1,A\n10,5,6,7,8,A,A,1,A\n26980,9,9,9,9,A,A,1,A\n')
        self.assertEqual(self.mod.read_tracks(path)[0]['points'], [[0., 0., 1., 2.], [10., 10 / 29.97, 5., 6.]])

    def test_tracks_and_frames_are_canonical(self):
        path = self.csv('10,1,2,3,4,Z,Z,1,Z\n10,1,2,3,4,A,A,1,A\n0,3,4,1,2,A,A,1,A\n')
        got = self.mod.read_tracks(path)
        self.assertEqual([t['track_id'] for t in got], ['A:leader', 'Z:leader'])
        self.assertEqual([p[0] for p in got[0]['points']], [0., 10.])

    def test_duplicate_frames_are_rejected_not_silently_dropped(self):
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            self.mod.read_tracks(self.csv('0,1,2,3,4,A,A,1,A\n0,1,2,3,4,A,A,1,A\n'))

    def test_nonfinite_coordinates_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'finite'):
            self.mod.read_tracks(self.csv('0,NaN,2,3,4,A,A,1,A\n'))

    def test_negative_frames_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'frame'):
            self.mod.read_tracks(self.csv('-10,1,2,3,4,A,A,1,A\n'))

    def test_missing_columns_are_rejected(self):
        path = self.root / 'bad.csv'; path.write_text('Frame,LeaderX\n0,1\n')
        with self.assertRaisesRegex(ValueError, 'columns'):
            self.mod.read_tracks(path)

    def test_compressed_metadata_retains_original_digest_and_fields(self):
        self.assertTrue(hasattr(self.mod, "read_metadata"), "metadata reader is missing")
        path = MODULE.with_name("profile-metadata.json.gz")
        got = self.mod.read_metadata(path)
        self.assertEqual(got["sha256"], "48161dc5f7319157a97c29ba52dc16e0990a266bf8310ff4ab179d82c170d1ed")
        self.assertEqual(got["input"]["tracks"], [])
        self.assertEqual(len(got["input"]["tables"]), 15)

    def test_corrupt_metadata_cannot_be_used(self):
        self.assertTrue(hasattr(self.mod, "read_metadata"), "metadata reader is missing")
        import gzip
        path = self.root / "metadata.json.gz"
        path.write_bytes(gzip.compress(b"{}", mtime=0))
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.mod.read_metadata(path)

    def test_digest_verifies_bytes(self):
        path = self.root / 'bytes'; path.write_bytes(b'original')
        expected = hashlib.sha256(b'original').hexdigest()
        self.mod.verify_digest(path, expected)
        path.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.mod.verify_digest(path, expected)

    def test_publication_preflights_all_files_before_writing(self):
        source = self.root / 'stage'; source.mkdir()
        dest = self.root / 'output'; dest.mkdir()
        (source / 'a').write_bytes(b'one'); (source / 'b').write_bytes(b'two')
        (dest / 'b').write_bytes(b'newer work')
        with self.assertRaisesRegex(ValueError, 'existing'):
            self.mod.publish_identical(source, dest, ['a', 'b'])
        self.assertFalse((dest / 'a').exists())
        self.assertEqual((dest / 'b').read_bytes(), b'newer work')

    def test_publication_is_idempotent(self):
        source = self.root / 'stage'; source.mkdir()
        dest = self.root / 'output'; dest.mkdir()
        (source / 'a').write_bytes(b'one')
        self.mod.publish_identical(source, dest, ['a'])
        self.mod.publish_identical(source, dest, ['a'])
        self.assertEqual((dest / 'a').read_bytes(), b'one')


if __name__ == '__main__':
    unittest.main()
