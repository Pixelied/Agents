"""Rust/Cargo JSON is UTF-8 even when the host's preferred text encoding is not."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import test_packaging

ROOT = Path(__file__).resolve().parents[2]


def legacy_locale_environment():
    environment = os.environ.copy()
    environment.update({'LC_ALL': 'C', 'LANG': 'C', 'PYTHONUTF8': '0',
                        'PYTHONCOERCECLOCALE': '0', 'PYTHONIOENCODING': 'utf-8',
                        'CARGO_NET_OFFLINE': 'true'})
    return environment


class Utf8PortabilityTests(unittest.TestCase):
    def test_package_reports_decode_utf8_independently_of_preferred_host_encoding(self):
        with tempfile.TemporaryDirectory() as name:
            resources = test_packaging.PackagingTests().resource_fixture(Path(name))
            report = resources / 'creature-profiles/runtime-profiles.report.json'
            value = json.loads(report.read_text(encoding='utf-8'))
            value['source_label_for_test'] = '\u01cd measured data'
            report.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
            code = ('import sys; from pathlib import Path; '
                    'sys.path.insert(0, sys.argv[1]); '
                    'from verify_package import verify_resources; '
                    'verify_resources(Path(sys.argv[2]))')
            result = subprocess.run([sys.executable, '-c', code, str(ROOT / 'scripts'),
                                     str(resources)], env=legacy_locale_environment(),
                                    capture_output=True, encoding='utf-8')
            self.assertEqual(result.returncode, 0, result.stderr)

    @unittest.skipUnless(shutil.which('cargo'), 'Rust/Cargo is required for the real metadata subprocess')
    def test_real_cargo_metadata_decodes_unicode_without_locale_assumptions(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            app = root / 'app'
            (app / 'src').mkdir(parents=True)
            (app / 'src/lib.rs').write_text('// Empty dependency-free metadata fixture.\n', encoding='utf-8')
            (app / 'Cargo.toml').write_text(
                '[package]\nname="desktop-app"\nversion="0.0.0"\nedition="2021"\n'
                'license="MIT"\ndescription="UTF-8 metadata \u01cd"\n', encoding='utf-8')
            (root / 'LICENSE').write_text('Test-only MIT notice for \u01cd.\n', encoding='utf-8')
            lock = subprocess.run(['cargo', 'generate-lockfile', '--offline', '--manifest-path',
                                   str(app / 'Cargo.toml')], capture_output=True, encoding='utf-8')
            self.assertEqual(lock.returncode, 0, lock.stderr)
            output = root / 'notices'
            result = subprocess.run([sys.executable, str(ROOT / 'scripts/collect_licenses.py'),
                                     '--manifest', str(app / 'Cargo.toml'),
                                     '--target', 'x86_64-pc-windows-msvc', '--output', str(output)],
                                    env=legacy_locale_environment(), capture_output=True, encoding='utf-8')
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((output / 'licenses/desktop-app-0.0.0/LICENSE').read_bytes(),
                             (root / 'LICENSE').read_bytes())


if __name__ == '__main__':
    unittest.main()
