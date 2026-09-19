import importlib.util,json,pathlib,plistlib,struct,tempfile,unittest,xml.etree.ElementTree as E
ROOT=pathlib.Path(__file__).resolve().parents[2]
def module(name):
 s=importlib.util.spec_from_file_location(name,ROOT/'scripts'/f'{name}.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
class ReleaseStructureTests(unittest.TestCase):
 def test_mac_metadata_is_a_background_utility(self):
  p=plistlib.loads((ROOT/'app/packaging/macos/Info.plist').read_bytes())
  self.assertIs(p['LSUIElement'],True);self.assertEqual(p['CFBundleExecutable'],'InsectRealism');self.assertEqual(p['CFBundlePackageType'],'APPL')
 def test_installer_is_per_user_without_services_or_elevation(self):
  tree=E.parse(ROOT/'app/packaging/windows/wix/Product.wxs');package=next(x for x in tree.iter() if x.tag.endswith('}Package'))
  self.assertEqual(package.attrib['Scope'],'perUser')
  for element in tree.iter():
   self.assertNotIn(element.tag.rsplit('}',1)[-1],['ServiceInstall','ServiceControl'])
   self.assertNotEqual(element.attrib.get('Root'),'HKLM')
 def test_harvest_is_deterministic_and_contains_every_staged_file(self):
  m=module('harvest_wix')
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);stage=root/'stage';(stage/'assets/presets').mkdir(parents=True)
   (stage/'insect-realism.exe').write_bytes(b'test fixture, not executable');(stage/'assets/presets/a.toml').write_text('x=1')
   a,b=root/'a.wxs',root/'b.wxs';m.harvest(stage,a);m.harvest(stage,b);self.assertEqual(a.read_bytes(),b.read_bytes())
   files=[e for e in E.parse(a).iter() if e.tag.endswith('}File')];self.assertEqual(len(files),2)
   self.assertTrue(any(f.attrib['Id']=='ApplicationExe' for f in files))
 def test_own_icons_have_valid_container_headers(self):
  ico=(ROOT/'app/packaging/icons/InsectRealism.ico').read_bytes();self.assertEqual(struct.unpack_from('<HHH',ico),(0,1,1))
  icns=(ROOT/'app/packaging/icons/InsectRealism.icns').read_bytes();self.assertEqual(icns[:4],b'icns');self.assertEqual(struct.unpack_from('>I',icns,4)[0],len(icns))
 def test_final_manifest_catches_mutated_payload(self):
  m=module('verify_package')
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);(root/'a').write_text('changed');(root/'PACKAGE_SHA256.json').write_text(json.dumps({'a':'0'*64}))
   with self.assertRaises(ValueError):m.verify_manifest(root)
if __name__=='__main__':unittest.main()
