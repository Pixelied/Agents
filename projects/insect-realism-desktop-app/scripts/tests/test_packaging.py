"""Packaging contracts. Synthetic headers are test fixtures, never distributable executables."""
import importlib.util
import json
import hashlib
import shutil
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]

def verifier():
    spec=importlib.util.spec_from_file_location('verify_package', ROOT/'scripts/verify_package.py')
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class PackagingTests(unittest.TestCase):
    def test_scripts_exist_and_are_not_placeholders(self):
        for name in ['app/packaging/macos/Info.plist','app/packaging/macos/build-dmg.sh',
                     'app/packaging/windows/wix/Product.wxs','app/packaging/windows/build-msi.ps1']:
            p=ROOT/name
            self.assertTrue(p.is_file(), name)
            self.assertGreater(p.stat().st_size, 100, name)

    def test_pe_validation_rejects_console_and_truncated_inputs(self):
        v=verifier()
        data=bytearray(512); data[:2]=b'MZ';struct.pack_into('<I',data,0x3c,0x80)
        data[0x80:0x84]=b'PE\0\0';struct.pack_into('<H',data,0x84,0x8664)
        struct.pack_into('<H',data,0x94,240);struct.pack_into('<H',data,0x98,0x20b)
        struct.pack_into('<H',data,0x98+68,2)
        self.assertEqual(v.inspect_pe(bytes(data))['subsystem'],2)
        struct.pack_into('<H',data,0x98+68,3)
        with self.assertRaises(ValueError):v.inspect_pe(bytes(data))
        with self.assertRaises(ValueError):v.inspect_pe(b'MZ')
        with self.assertRaises(ValueError):v.inspect_pe(b'#!/bin/sh\n')

    def test_macho_architecture_and_filetype_are_checked(self):
        v=verifier()
        data=struct.pack('<IIIIIIII',0xfeedfacf,0x0100000c,0,2,0,0,0,0)
        self.assertEqual(v.inspect_macho(data)['architecture'],'arm64')
        with self.assertRaises(ValueError):v.inspect_macho(b'pretend binary')
        bad=struct.pack('<IIIIIIII',0xfeedfacf,0x0100000c,0,6,0,0,0,0)
        with self.assertRaises(ValueError):v.inspect_macho(bad)

    def test_windows_installer_is_per_user_and_does_not_remove_shared_run_key(self):
        tree=ET.parse(ROOT/'app/packaging/windows/wix/Product.wxs')
        ns={'w':'http://wixtoolset.org/schemas/v4/wxs'}
        package=tree.getroot().find('w:Package',ns)
        self.assertEqual(package.attrib.get('Scope'),'perUser')
        self.assertFalse(tree.findall('.//w:ServiceInstall',ns))
        for key in tree.findall('.//w:RemoveRegistryKey',ns):
            self.assertNotIn('CurrentVersion\\Run',key.attrib.get('Key',''))
        cleanup=tree.find('.//w:CustomAction[@Id="RemoveStartupEntry"]',ns)
        self.assertEqual(cleanup.attrib['ExeCommand'],'--uninstall-cleanup')
        self.assertEqual(cleanup.attrib['Impersonate'],'yes')

    def test_manifest_detects_payload_tampering_and_path_escape(self):
        v=verifier()
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'file.txt').write_text('payload')
            v.write_manifest(root)
            v.verify_manifest(root)
            (root/'file.txt').write_text('changed')
            with self.assertRaises(ValueError):v.verify_manifest(root)
            (root/'PACKAGE_SHA256.json').write_text(json.dumps({'../escape':'0'*64}))
            with self.assertRaises(ValueError):v.verify_manifest(root)


    def test_custom_output_paths_remain_absolute_across_build_directory_changes(self):
        mac=(ROOT/'app/packaging/macos/build-dmg.sh').read_text()
        win=(ROOT/'app/packaging/windows/build-msi.ps1').read_text()
        self.assertIn('DEST="$(cd "$DEST" && pwd)"', mac)
        self.assertLess(mac.index('DEST="$(cd "$DEST" && pwd)"'),mac.index('cd "$APP"'))
        self.assertIn('$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path', win)
        self.assertLess(win.index('$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path'), win.index('Push-Location $App'))

    def resource_fixture(self, base):
        root=base/'Resources'
        shutil.copytree(ROOT/'app/assets/creature-profiles',root/'creature-profiles')
        shutil.copytree(ROOT/'app/assets/presets',root/'presets')
        shutil.copytree(ROOT/'app/crates/rendering/shaders',root/'shaders')
        for name in ('BIOLOGY_NOTICES.md','APP_LICENSE.txt','THIRD_PARTY_NOTICES.md'):
            (root/name).write_text('Test-only notice, not a distributable package.')
        report=json.loads((root/'creature-profiles/runtime-profiles.report.json').read_text())
        (root/'versions.json').write_text(json.dumps({'app_version':'0.1.0',
            'profile_version':report['profile_version'],'profile_schema':report['schema_version'],
            'profile_sha256':report['sha256']}))
        return root

    def test_profile_payload_digest_is_validated_independently_of_sidecar(self):
        v=verifier()
        with tempfile.TemporaryDirectory() as d:
            root=self.resource_fixture(Path(d))
            v.verify_resources(root)
            profile=root/'creature-profiles/runtime-profiles.bin'
            data=bytearray(profile.read_bytes()); data[-1] ^= 1
            profile.write_bytes(data)
            # Recomputing an external manifest must not hide an invalid internal envelope.
            digest=hashlib.sha256(data).hexdigest()
            for path in (root/'creature-profiles/runtime-profiles.report.json', root/'versions.json'):
                value=json.loads(path.read_text())
                value['sha256' if 'sha256' in value else 'profile_sha256']=digest
                path.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError,'payload digest'):
                v.verify_resources(root)

    def test_profile_envelope_length_is_checked(self):
        v=verifier()
        with tempfile.TemporaryDirectory() as d:
            root=self.resource_fixture(Path(d))
            profile=root/'creature-profiles/runtime-profiles.bin'
            data=bytearray(profile.read_bytes()); struct.pack_into('<Q',data,8,len(data))
            profile.write_bytes(data)
            report=root/'creature-profiles/runtime-profiles.report.json'
            value=json.loads(report.read_text()); value['sha256']=hashlib.sha256(data).hexdigest()
            report.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError,'payload length'):
                v.verify_resources(root)

    def test_profile_version_metadata_is_bound_to_payload_digest(self):
        v=verifier()
        with tempfile.TemporaryDirectory() as d:
            root=self.resource_fixture(Path(d))
            path=root/'versions.json'; versions=json.loads(path.read_text())
            versions['profile_sha256']='0'*64; path.write_text(json.dumps(versions))
            with self.assertRaisesRegex(ValueError,'profile version metadata'):
                v.verify_resources(root)

if __name__=='__main__':unittest.main()
