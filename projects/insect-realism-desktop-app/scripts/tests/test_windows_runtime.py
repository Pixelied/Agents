"""PE import records, not string searches, enforce the portable runtime policy."""
import importlib.util
from pathlib import Path
import struct
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'verify_package.py'


def pe_with_import(name='KERNEL32.dll', delay=False):
    data = bytearray(2048)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 60, 128)
    data[128:132] = b'PE\0\0'
    struct.pack_into('<HH', data, 132, 0x8664, 1)
    struct.pack_into('<H', data, 148, 240)
    opt = 152
    struct.pack_into('<H', data, opt, 0x20b)
    struct.pack_into('<H', data, opt + 68, 2)
    struct.pack_into('<I', data, opt + 60, 512)
    struct.pack_into('<I', data, opt + 108, 16)
    section = opt + 240
    data[section:section+8] = b'.rdata\0\0'
    struct.pack_into('<IIII', data, section + 8, 1024, 4096, 1024, 512)
    directory = 13 if delay else 1
    struct.pack_into('<II', data, opt + 112 + 8 * directory, 4096, 64 if delay else 40)
    struct.pack_into('<I', data, 512 + (4 if delay else 12), 4224)
    if delay:
        struct.pack_into('<I', data, 512, 1)
    encoded = name.encode('ascii') + b'\0'
    data[640:640+len(encoded)] = encoded
    return data


class WindowsRuntimeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('runtime_package', SCRIPT)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_dynamic_visual_cpp_runtime_is_not_a_portable_package(self):
        for name in ['VCRUNTIME140.dll', 'vcruntime140_1.dll', 'MSVCP140.dll', 'CONCRT140.dll']:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'runtime'):
                self.module.inspect_pe(pe_with_import(name))

    def test_delayed_runtime_import_also_fails(self):
        with self.assertRaisesRegex(ValueError, 'runtime'):
            self.module.inspect_pe(pe_with_import('VCRUNTIME140.dll', delay=True))

    def test_system_imports_are_recorded(self):
        details = self.module.inspect_pe(pe_with_import())
        self.assertEqual(details.get('imported_dlls'), ['KERNEL32.dll'])

    def test_literal_runtime_text_is_not_an_import(self):
        data = pe_with_import()
        data[1700:1716] = b'VCRUNTIME140.dll'
        details = self.module.inspect_pe(data)
        self.assertEqual(details.get('imported_dlls'), ['KERNEL32.dll'])

    def test_invalid_name_rva_is_rejected(self):
        data = pe_with_import()
        struct.pack_into('<I', data, 524, 0xFFFF0000)
        with self.assertRaisesRegex(ValueError, 'RVA'):
            self.module.inspect_pe(data)

    def test_packager_scopes_static_crt_to_target_build(self):
        # Wiring guard only; the native job must inspect the resulting PE.
        source = (SCRIPT.parents[1] / 'app/packaging/windows/build-msi.ps1').read_text()
        self.assertIn('target-feature=+crt-static', source)
        self.assertIn('CARGO_ENCODED_RUSTFLAGS', source)
        self.assertIn('$env:RUSTFLAGS = $PreviousRustFlags', source)

    def test_truncated_import_table_is_rejected(self):
        data = pe_with_import()
        struct.pack_into('<II', data, 152 + 112 + 8, 4096, 19)
        with self.assertRaisesRegex(ValueError, 'import'):
            self.module.inspect_pe(data)


if __name__ == '__main__':
    unittest.main(verbosity=2)
