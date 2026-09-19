"""Actual filesystem regressions exposed by native CI, also reproducible on POSIX."""
from pathlib import Path
import importlib.util
import os
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


class NativePortabilityTests(unittest.TestCase):
    def test_checkpoint_sync_uses_a_writable_descriptor_without_changing_bytes(self):
        tool = module('make_checkpoint')
        native_fsync = os.fsync
        calls = []

        def require_writable(fd):
            # Real OS descriptor probe: a read-only fd rejects even a zero-byte
            # write on POSIX, reproducing Windows _commit's writable-fd rule.
            try:
                os.write(fd, b'')
            except OSError as error:
                self.fail('Checkpoint sync descriptor is not writable: ' + str(error))
            calls.append(fd)
            native_fsync(fd)

        with tempfile.TemporaryDirectory() as name:
            base = Path(name)
            root = base / 'source'
            root.mkdir()
            (root / 'file.rs').write_bytes(b'preserved checkpoint bytes')
            output = base / 'checkpoint.zip'
            with patch.object(tool.os, 'fsync', side_effect=require_writable):
                tool.checkpoint(root, output)
            self.assertEqual(len(calls), 1)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.read('source/file.rs'), b'preserved checkpoint bytes')

    def test_locator_resolves_a_real_alias_without_escaping_the_checkout(self):
        tool = module('verify_release')
        with tempfile.TemporaryDirectory() as name:
            base = Path(name)
            actual = base / 'actual'
            project = actual / 'projects/current-app'
            (project / 'app').mkdir(parents=True)
            (project / 'app/Cargo.toml').write_text('[workspace]\n', encoding='utf-8')
            alias = base / 'alias'
            try:
                alias.symlink_to(actual, target_is_directory=True)
            except OSError as error:
                if sys_platform_is_windows_privilege_error(error):
                    self.skipTest('Native Windows symbolic-link privilege not granted')
                raise
            selected = tool.find_project(alias)
            self.assertEqual(selected, project.resolve())
            self.assertTrue(selected.is_relative_to(actual.resolve()))


def sys_platform_is_windows_privilege_error(error):
    return os.name == 'nt' and getattr(error, 'winerror', None) == 1314


if __name__ == '__main__':
    unittest.main()
