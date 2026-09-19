import importlib.util,pathlib,shutil,tempfile,unittest,os
SCRIPT=pathlib.Path(__file__).resolve().parents[1]/'qualify_secondary.py'
PACK=pathlib.Path(os.environ.get('INSECT_MEGA_PACK',str(pathlib.Path(__file__).resolve().parents[2]/'INSECT_REALISM_MEGA_PACK')))

@unittest.skipUnless(PACK.is_dir(),'Original Mega Pack required; set INSECT_MEGA_PACK')
class SecondaryGateTests(unittest.TestCase):
 def module(self):
  self.assertTrue(SCRIPT.exists(),'secondary gate not implemented')
  spec=importlib.util.spec_from_file_location('gate',SCRIPT);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
 def test_all_candidates_reviewed_none_silently_qualified(self):
  m=self.module();report=m.evaluate(PACK)
  self.assertEqual(len(report['candidates']),5)
  self.assertEqual(report['shipping_creatures'],['ant'])
  for candidate in report['candidates']:
   self.assertFalse(candidate['qualified']);self.assertTrue(candidate['reasons']);self.assertTrue(candidate['source_note_sha256'])
 def test_report_is_deterministic(self):
  m=self.module();self.assertEqual(m.evaluate(PACK),m.evaluate(PACK))
 def test_missing_license_is_rejected_not_assumed_reusable(self):
  m=self.module()
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)
   shutil.copytree(PACK/'03_OTHER_TINY_CREATURES',p/'03_OTHER_TINY_CREATURES')
   shutil.copytree(PACK/'00_MASTER_INDEX',p/'00_MASTER_INDEX')
   (p/'00_MASTER_INDEX/LICENSE_MANIFEST.csv').write_text('source_id,spdx,status,shipping_allowed,notes\n')
   with self.assertRaises(ValueError):m.evaluate(p)
 def test_changed_evidence_cannot_reuse_the_previous_review(self):
  m=self.module()
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)
   shutil.copytree(PACK/'03_OTHER_TINY_CREATURES',p/'03_OTHER_TINY_CREATURES')
   shutil.copytree(PACK/'00_MASTER_INDEX',p/'00_MASTER_INDEX')
   note=p/'00_MASTER_INDEX/source_notes/secondary-drosophila-treadmill-2024.md'
   note.write_text(note.read_text(encoding='utf-8')+'\nNew evidence not reviewed by this release.\n',encoding='utf-8')
   with self.assertRaisesRegex(ValueError,'review'):
    m.evaluate(p)
if __name__=='__main__':unittest.main()
