from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"
MARKERS = [
    "MEDUSA_PHASE2_OK",
    "MEDUSA_PHASE3_OK",
    "MEDUSA_DEATH_STATE_OK",
    "MEDUSA_REWARD_GUARD_OK",
    "MEDUSA_EYE_RECOVERY_OK",
    "MEDUSA_RITUAL_REFUND_OK",
    "MEDUSA_RESTART_RECOVERY_OK",
    "MEDUSA_INSTANCE_ISOLATION_OK",
    "MEDUSA_EYE_INSTANCE_ID_OK",
]


class LifecycleRuntimeContract(unittest.TestCase):
    def test_runtime_smoke_covers_lifecycle_and_instance_isolation(self):
        master = FN / "debug/test_lifecycle.mcfunction"
        scoped = FN / "debug/test_lifecycle_instance.mcfunction"
        self.assertTrue(master.is_file(), "lifecycle runtime smoke entrypoint is missing")
        self.assertTrue(scoped.is_file(), "instance-scoped lifecycle runtime smoke is missing")
        self.assertIn("function medusa:debug/test_lifecycle_instance", master.read_text())
        text = scoped.read_text()
        for marker in MARKERS:
            self.assertIn(marker, text)

        continuation = (FN / "debug/continue_smoke.mcfunction").read_text()
        self.assertIn("function medusa:debug/test_lifecycle", continuation)
        self.assertLess(
            continuation.find("function medusa:debug/test_gaze_pipeline"),
            continuation.find("function medusa:debug/test_lifecycle"),
            "lifecycle smoke must run after gaze behavior is proven",
        )
        self.assertLess(
            continuation.find("function medusa:debug/test_lifecycle"),
            continuation.find("MEDUSA_SMOKE_DONE"),
            "lifecycle smoke must finish before the overall smoke marker",
        )

    def test_ci_requires_lifecycle_runtime_markers(self):
        workflow_path = REPO / ".github/workflows/medusa-26-1-2-ci.yml"
        if not workflow_path.is_file():
            self.skipTest("repo-level GitHub Actions workflow is not bundled in the standalone source archive")
        workflow = workflow_path.read_text()
        for marker in MARKERS:
            self.assertIn(f"grep -q '{marker}' server.log", workflow)


if __name__ == "__main__":
    unittest.main()
