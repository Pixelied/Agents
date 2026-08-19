from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
WORKFLOW = REPO / ".github/workflows/medusa-26-1-2-ci.yml"


class PackagingContract(unittest.TestCase):
    def workflow_text(self):
        if not WORKFLOW.is_file():
            self.skipTest("repo-level GitHub Actions workflow is not bundled in the standalone source archive")
        return WORKFLOW.read_text()

    def test_ci_builds_separate_direct_install_archives(self):
        text = self.workflow_text()
        self.assertIn("medusa-datapack-26.1.2.zip", text)
        self.assertIn("medusa-resourcepack-26.1.2.zip", text)
        self.assertIn("medusa-26.1.2-source.zip", text)
        self.assertIn("cd datapacks/medusa", text)
        self.assertIn("cd resourcepacks/medusa", text)
        self.assertIn("unzip -p", text)
        self.assertIn("pack.mcmeta", text)

    def test_install_archives_exclude_macos_metadata(self):
        text = self.workflow_text()
        self.assertIn(".DS_Store", text)
        self.assertIn("*/.DS_Store", text)


if __name__ == "__main__":
    unittest.main()
