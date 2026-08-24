from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"
MARKERS = [
    "MEDUSA_STAFF_QUICK_ISOLATION_OK",
    "MEDUSA_STAFF_NOAI_COMPAT_OK",
    "MEDUSA_STAFF_BOSS_LIMIT_OK",
]


class StaffRuntimeContract(unittest.TestCase):
    def test_runtime_smoke_executes_staff_target_behaviors(self):
        smoke = FN / "debug/test_staff_runtime.mcfunction"
        self.assertTrue(smoke.is_file(), "Staff exact-runtime smoke is missing")
        text = smoke.read_text()
        for marker in MARKERS:
            self.assertIn(marker, text)
        continuation = (FN / "debug/continue_smoke.mcfunction").read_text()
        self.assertIn("function medusa:debug/test_staff_runtime", continuation)
        self.assertLess(
            continuation.find("function medusa:debug/test_gaze_pipeline"),
            continuation.find("function medusa:debug/test_staff_runtime"),
        )
        self.assertLess(
            continuation.find("function medusa:debug/test_staff_runtime"),
            continuation.find("function medusa:debug/test_lifecycle"),
        )

    def test_ci_requires_staff_runtime_markers(self):
        workflow_path = REPO / ".github/workflows/medusa-26-1-2-ci.yml"
        if not workflow_path.is_file():
            self.skipTest("repo-level GitHub Actions workflow is not bundled in the standalone source archive")
        workflow = workflow_path.read_text()
        for marker in MARKERS:
            self.assertIn(f"grep -q '{marker}' server.log", workflow)


if __name__ == "__main__":
    unittest.main()
