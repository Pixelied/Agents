import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
def module():
    s=importlib.util.spec_from_file_location('collect_licenses',ROOT/'scripts/collect_licenses.py')
    m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
class LicenseTests(unittest.TestCase):
    def test_explicit_choices_and_combined_obligations(self):
        m=module()
        self.assertEqual(m.select_license('MIT OR GPL-3.0-only'),['MIT'])
        self.assertIsNone(m.select_license('MIT AND GPL-3.0-only'))
        self.assertEqual(m.select_license('(MIT OR Apache-2.0) AND Unicode-3.0'),['MIT','Unicode-3.0'])
        self.assertEqual(m.select_license('Apache-2.0 WITH LLVM-exception'),['Apache-2.0 WITH LLVM-exception'])
        self.assertIsNone(m.select_license('UNKNOWN'))
        self.assertIsNone(m.select_license('MIT OR'))
    def test_font_binaries_are_not_license_text(self):
        m=module()
        self.assertFalse(m.is_license_file(Path('Ubuntu-LICENSE.ttf')))
        self.assertTrue(m.is_license_file(Path('fonts/UFL.txt')))
        self.assertTrue(m.is_license_file(Path('LICENSE-MIT')))
    def test_legacy_cargo_slash_and_font_obligations(self):
        m=module()
        self.assertEqual(m.select_license('MIT/Apache-2.0'), ['MIT'])
        self.assertEqual(m.select_license('Apache-2.0 / MIT'), ['MIT'])
        self.assertEqual(m.select_license('Unlicense/MIT'), ['MIT'])
        self.assertEqual(m.select_license('(MIT OR Apache-2.0) AND OFL-1.1 AND Ubuntu-font-1.0'),
                         ['MIT','OFL-1.1','Ubuntu-font-1.0'])
        self.assertEqual(m.select_license('MPL-2.0'), ['MPL-2.0'])
        self.assertTrue(m.is_license_file(Path('fonts/emoji-icon-font-mit-license.txt')))
        self.assertTrue(m.is_license_file(Path('fonts/Hack-Regular.txt')))
    def test_runtime_dependency_graph_excludes_dev_only_edges(self):
        m=module()
        nodes={'app':{'deps':[{'pkg':'normal','dep_kinds':[{'kind':None}]},
                              {'pkg':'dev','dep_kinds':[{'kind':'dev'}]},
                              {'pkg':'build','dep_kinds':[{'kind':'build'}]}]},
               'normal':{'deps':[]},'build':{'deps':[]},'dev':{'deps':[]}}
        self.assertEqual(m.application_dependencies('app', nodes), {'app','normal','build'})
    def test_prefers_mit_only_when_it_is_a_complete_or_branch(self):
        m=module()
        self.assertEqual(m.select_license('Zlib OR Apache-2.0 OR MIT'),['MIT'])
        self.assertEqual(m.select_license('Apache-2.0 OR (MIT AND GPL-3.0-only)'),['Apache-2.0'])
        self.assertEqual(m.select_license('(Apache-2.0 OR MIT) AND OFL-1.1'),['MIT','OFL-1.1'])
