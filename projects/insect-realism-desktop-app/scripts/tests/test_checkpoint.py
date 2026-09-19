"""Checkpoint safety tests: temporary projects only, no real build data are modified."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]

class CheckpointTests(unittest.TestCase):
    def test_nested_archives_and_fonts_are_not_exported(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            root = base / 'project'
            (root / 'scripts').mkdir(parents=True)
            script = root / 'scripts/make_checkpoint.py'
            shutil.copy2(ROOT / 'scripts/make_checkpoint.py', script)
            (root / 'source.rs').write_text('fn main() {}')
            (root / 'previous.zip').write_bytes(b'not part of source')
            (root / 'font.ttc').write_bytes(b'font fixture, not real font')
            output = base / 'checkpoint.zip'
            subprocess.run([sys.executable, str(script), str(output)], check=True, capture_output=True)
            with zipfile.ZipFile(output) as archive:
                names = archive.namelist()
                self.assertNotIn('project/previous.zip', names)
                self.assertNotIn('project/font.ttc', names)
                self.assertNotIn('project/external-link.txt', names)
                self.assertIn('project/source.rs', names)
                manifest = json.loads(archive.read('CHECKPOINT_SHA256.json'))
                for name, digest in manifest.items():
                    self.assertEqual(hashlib.sha256(archive.read('project/' + name)).hexdigest(), digest)

    def test_external_symlinks_are_not_exported(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            root = base / 'project'
            (root / 'scripts').mkdir(parents=True)
            script = root / 'scripts/make_checkpoint.py'
            shutil.copy2(ROOT / 'scripts/make_checkpoint.py', script)
            external = base / 'external-private.txt'
            external.write_text('outside project')
            try:
                (root / 'external-link.txt').symlink_to(external)
            except OSError as error:
                # Windows can require Developer Mode/admin to create symlinks; the other
                # archive tests still execute. Never claim that unexercised case passed.
                if sys.platform == 'win32' and getattr(error, 'winerror', None) == 1314:
                    self.skipTest('Windows host did not grant symbolic-link creation privilege')
                raise
            output = base / 'checkpoint.zip'
            subprocess.run([sys.executable, str(script), str(output)], check=True, capture_output=True)
            with zipfile.ZipFile(output) as archive:
                self.assertNotIn('project/external-link.txt', archive.namelist())

class CheckpointDurabilityTests(unittest.TestCase):
    def tool(self):
        import importlib.util
        spec=importlib.util.spec_from_file_location('checkpoint_tool',ROOT/'scripts/make_checkpoint.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        return module

    def test_machine_cargo_config_and_credentials_are_excluded(self):
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);root=base/'project';(root/'app/.cargo').mkdir(parents=True)
            (root/'app/.cargo/credentials.toml').write_text('fixture-not-a-real-token')
            (root/'app/.cargo/config.toml').write_text('directory="/machine-specific/path"')
            (root/'app/source.rs').write_text('fn main() {}')
            output=base/'checkpoint.zip';self.tool().checkpoint(root,output)
            with zipfile.ZipFile(output) as archive:
                self.assertFalse(any('/.cargo/' in name for name in archive.namelist()))
                self.assertIn('project/app/source.rs',archive.namelist())

    def test_equal_content_ignores_source_mtime_and_preserves_available_permissions(self):
        import os
        with tempfile.TemporaryDirectory() as d:
            base=Path(d);root=base/'project';root.mkdir()
            script=root/'build.sh';script.write_text('#!/bin/sh\necho checked\n');script.chmod(0o755)
            a=base/'a.zip';b=base/'b.zip'
            self.tool().checkpoint(root,a)
            os.utime(script,(946684800,946684800))
            self.tool().checkpoint(root,b)
            self.assertEqual(a.read_bytes(),b.read_bytes())
            with zipfile.ZipFile(a) as archive:
                self.assertEqual(bool((archive.getinfo('project/build.sh').external_attr >> 16) & 0o111),
                                 bool(script.stat().st_mode & 0o111))

    def test_source_file_cannot_be_overwritten_as_archive(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);source=root/'source.rs';source.write_text('preserve this source')
            with self.assertRaises(ValueError):self.tool().checkpoint(root,source)
            self.assertEqual(source.read_text(),'preserve this source')

if __name__ == '__main__':
    unittest.main()
