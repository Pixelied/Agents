import importlib.util,pathlib,struct,tempfile,unittest
SCRIPT=pathlib.Path(__file__).resolve().parents[1]/'verify_package.py'
class PackageTests(unittest.TestCase):
 def module(self):
  self.assertTrue(SCRIPT.exists(),'package validation/staging not implemented')
  s=importlib.util.spec_from_file_location('package',SCRIPT);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
 def pe(self,subsystem=2):
  b=bytearray(512);b[:2]=b'MZ';struct.pack_into('<I',b,60,128);b[128:132]=b'PE\0\0';struct.pack_into('<HH',b,132,0x8664,1);struct.pack_into('<HH',b,148,240,0x22);struct.pack_into('<H',b,152,0x20b);struct.pack_into('<H',b,220,subsystem);return b
 def macho(self):
  b=bytearray(64);struct.pack_into('<IIIIIIII',b,0,0xfeedfacf,0x0100000c,0,2,1,32,0,0);return b
 def test_linux_cannot_be_mislabelled_as_native_release(self):
  m=self.module()
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'fake';p.write_bytes(b'\x7fELF'+bytes(100))
   for platform in ['macos','windows']:
    with self.assertRaises(ValueError):(m.inspect_macho if platform == 'macos' else m.inspect_pe)(p.read_bytes())
 def test_windows_requires_x64_gui_not_console(self):
  m=self.module()
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'test.exe';p.write_bytes(self.pe());self.assertEqual(m.inspect_pe(p.read_bytes())['subsystem'],2)
   p.write_bytes(self.pe(3))
   with self.assertRaises(ValueError):m.inspect_pe(p.read_bytes())
 def test_apple_silicon_requires_macho_executable(self):
  m=self.module()
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'test';p.write_bytes(self.macho());self.assertEqual(m.inspect_macho(p.read_bytes())['architecture'],'arm64')
   p.write_bytes(self.macho()[:12])
   with self.assertRaises(ValueError):m.inspect_macho(p.read_bytes())
 def test_missing_attribution_blocks_staging(self):
  m=self.module()
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);p=root/'test.exe';p.write_bytes(self.pe()+bytes(4096))
   from stage_package import stage
   with self.assertRaisesRegex(ValueError, 'collect dependency licenses'):
    stage(p,root/'out','windows',root/'absent-notices')
if __name__=='__main__':unittest.main()
