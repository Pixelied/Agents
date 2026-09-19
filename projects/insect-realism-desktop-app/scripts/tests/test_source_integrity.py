"""Source packaging regressions: valid manifests and preserved release tooling."""
import pathlib
import tomllib
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
class SourceIntegrityTests(unittest.TestCase):
    def test_all_workspace_manifests_parse(self):
        for manifest in sorted((ROOT / 'app').rglob('Cargo.toml')):
            with self.subTest(manifest=str(manifest.relative_to(ROOT))):
                try:
                    tomllib.loads(manifest.read_text(encoding='utf-8'))
                except tomllib.TOMLDecodeError as error:
                    self.fail(str(error))
    def test_native_release_acceptance_helpers_are_preserved(self):
        for name in ['app/crates/desktop-app/examples/input_probe.rs',
                     'app/crates/desktop-app/src/soak.rs',
                     'scripts/verify_package.py', 'scripts/release_checksums.py']:
            with self.subTest(name=name):
                self.assertTrue((ROOT/name).is_file(), name+' absent from packaged source')
if __name__ == '__main__': unittest.main()
