"""Temporary synthetic executable fixtures exercise XML generation, not native installer acceptance."""
from pathlib import Path
import importlib.util
import shutil
import struct
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from stage_package import stage
from verify_package import write_manifest
from wix_payload import generate

NS = {'w': 'http://wixtoolset.org/schemas/v4/wxs'}

class WixPayloadTests(unittest.TestCase):
    def test_every_created_directory_has_nonrecursive_uninstall_cleanup(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            binary = base / 'synthetic-test-fixture.exe'
            payload = bytearray(8192)
            payload[:2] = b'MZ'
            struct.pack_into('<I', payload, 0x3c, 0x80)
            payload[0x80:0x84] = b'PE\0\0'
            struct.pack_into('<H', payload, 0x84, 0x8664)
            struct.pack_into('<H', payload, 0x94, 240)
            struct.pack_into('<H', payload, 0x98, 0x20b)
            struct.pack_into('<H', payload, 0x98 + 68, 2)
            binary.write_bytes(payload)
            notices = base / 'notices'
            (notices / 'licenses').mkdir(parents=True)
            (notices / 'THIRD_PARTY_NOTICES.md').write_text('Synthetic test notices; not distributable.')
            root = base / 'payload'
            stage(binary, root, 'windows', notices)
            self.assertEqual((root/'Resources/APP_LICENSE.txt').read_bytes() if (root/'Resources/APP_LICENSE.txt').is_file() else b'', (ROOT/'LICENSE').read_bytes(), 'Application MIT notice must travel with the executable')
            write_manifest(root)
            output = base / 'Payload.wxs'
            generate(root, output)
            tree = ET.parse(output)
            directories = {'INSTALLFOLDER'} | {n.attrib['Id'] for n in tree.findall('.//w:Directory', NS)}
            removals = {n.attrib.get('Directory') for n in tree.findall('.//w:RemoveFolder', NS)}
            self.assertEqual(directories, removals)
            self.assertTrue(all(n.attrib['On'] == 'uninstall' for n in tree.findall('.//w:RemoveFolder', NS)))
            # No wildcard/delete-tree cleanup can destroy user files added after installation.
            self.assertFalse(tree.findall('.//w:RemoveFile', NS))
            first = output.read_bytes()
            generate(root, output)
            self.assertEqual(first, output.read_bytes())

if __name__ == '__main__':
    unittest.main()
