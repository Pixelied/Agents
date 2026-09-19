"""Existing original icons must survive staging. Headers here are test-only fixtures."""
import importlib.util
from pathlib import Path
import plistlib
import struct
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import stage_package
import verify_package


class PackageIconTests(unittest.TestCase):
    def stage_fixture(self, base, platform):
        data = bytearray(4096)
        if platform == 'windows':
            data[:2] = b'MZ'
            struct.pack_into('<I', data, 0x3c, 0x80)
            data[0x80:0x84] = b'PE\0\0'
            struct.pack_into('<H', data, 0x84, 0x8664)
            struct.pack_into('<H', data, 0x94, 240)
            struct.pack_into('<H', data, 0x98, 0x20b)
            struct.pack_into('<H', data, 0x98 + 68, 2)
        else:
            struct.pack_into('<8I', data, 0, 0xfeedfacf, 0x0100000c, 0, 2, 0, 0, 0, 0)
        binary = base / 'header-fixture-not-a-native-executable'
        binary.write_bytes(data)
        notices = base / 'notices'
        (notices / 'licenses').mkdir(parents=True)
        (notices / 'THIRD_PARTY_NOTICES.md').write_text('Test-only notice; not a release audit.')
        (notices / 'licenses/test.txt').write_text('Test-only license fixture')
        staged = base / 'staged'
        stage_package.stage(binary, staged, platform, notices)
        return staged

    def test_macos_staging_binds_info_plist_to_original_icon(self):
        with tempfile.TemporaryDirectory() as name:
            staged = self.stage_fixture(Path(name), 'macos')
            info = plistlib.loads((staged / 'Contents/Info.plist').read_bytes())
            self.assertEqual(info.get('CFBundleIconFile'), 'InsectRealism.icns')
            icon = staged / 'Contents/Resources/InsectRealism.icns'
            self.assertEqual(icon.read_bytes(), (ROOT / 'app/packaging/icons/InsectRealism.icns').read_bytes())
            self.assertFalse(verify_package.verify_package(staged, 'macos')['native_launch_verified'])
            icon.write_bytes(b'not an icon')
            with self.assertRaisesRegex(ValueError, 'icon'):
                verify_package.verify_package(staged, 'macos')

    def test_windows_staging_contains_valid_original_ico(self):
        with tempfile.TemporaryDirectory() as name:
            staged = self.stage_fixture(Path(name), 'windows')
            icon = staged / 'Resources/InsectRealism.ico'
            self.assertTrue(icon.is_file(), 'Windows resource icon was omitted')
            self.assertEqual(icon.read_bytes(), (ROOT / 'app/packaging/icons/InsectRealism.ico').read_bytes())
            self.assertFalse(verify_package.verify_package(staged, 'windows')['native_launch_verified'])
            icon.write_bytes(b'not an icon')
            with self.assertRaisesRegex(ValueError, 'icon'):
                verify_package.verify_package(staged, 'windows')

    def test_msi_shortcut_and_installed_apps_reference_the_packaged_icon(self):
        tree = ET.parse(ROOT / 'app/packaging/windows/wix/Product.wxs')
        ns = {'w': 'http://wixtoolset.org/schemas/v4/wxs'}
        icon = tree.find('.//w:Icon', ns)
        self.assertIsNotNone(icon, 'MSI must embed the original icon')
        shortcut = tree.find('.//w:Shortcut', ns)
        arp = tree.find('.//w:Property[@Id="ARPPRODUCTICON"]', ns)
        self.assertIsNotNone(arp)
        self.assertEqual(arp.attrib['Value'], icon.attrib['Id'])
        self.assertEqual(shortcut.attrib['Icon'], icon.attrib['Id'])
        script = (ROOT / 'app/packaging/windows/build-msi.ps1').read_text(encoding='utf-8')
        self.assertIn('AppIcon=', script)
        self.assertIn('Resources/InsectRealism.ico', script)


if __name__ == '__main__':
    unittest.main()
