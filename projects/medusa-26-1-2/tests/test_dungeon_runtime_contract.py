from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"

MAZE_MARKERS = [
    "MEDUSA_MAZE_CELLS_OK",
    "MEDUSA_MAZE_INITIAL_SOLVABLE_OK",
    "MEDUSA_MAZE_DELTA_OK",
    "MEDUSA_MAZE_OPEN_FIRST_OK",
    "MEDUSA_MAZE_WALL_DISPLAY_OK",
    "MEDUSA_MAZE_COLLISION_OK",
    "MEDUSA_MAZE_OCCUPIED_ABORT_OK",
    "MEDUSA_MAZE_INSTANCE_ISOLATION_OK",
    "MEDUSA_MAZE_RECOVERY_OK",
    "MEDUSA_MAZE_COMPLETE_OK",
]


class DungeonRuntimeContract(unittest.TestCase):
    def test_runtime_smoke_proves_shifting_maze(self):
        smoke = FN / "debug/test_dungeon_progression.mcfunction"
        instance_smoke = FN / "debug/test_dungeon_progression_instance.mcfunction"
        self.assertTrue(smoke.is_file(), "dungeon progression runtime smoke is missing")
        self.assertTrue(instance_smoke.is_file(), "instance-scoped dungeon progression smoke is missing")
        self.assertIn("function medusa:debug/test_dungeon_progression_instance", smoke.read_text())
        text = instance_smoke.read_text()
        for marker in MAZE_MARKERS:
            self.assertIn(marker, text)
        for legacy in ["MEDUSA_P1_GATE_OK", "MEDUSA_P2_GATE_OK", "MEDUSA_P3_GATE_OK"]:
            self.assertNotIn(legacy, text)

        loaded = (FN / "debug/create_test_temple_loaded.mcfunction").read_text()
        self.assertIn("function medusa:debug/test_dungeon_progression", loaded)
        self.assertLess(
            loaded.find("function medusa:debug/test_dungeon_progression"),
            loaded.find("function medusa:debug/start_test_boss"),
            "maze runtime proof must complete before the boss bypass smoke",
        )

    def test_ci_requires_shifting_maze_runtime_markers(self):
        workflow_path = REPO / ".github/workflows/medusa-26-1-2-ci.yml"
        if not workflow_path.is_file():
            self.skipTest("repo-level GitHub Actions workflow is not bundled in the standalone source archive")
        workflow = workflow_path.read_text()
        for marker in MAZE_MARKERS:
            self.assertIn(f"grep -q '{marker}' server.log", workflow)
        for legacy in ["MEDUSA_P1_GATE_OK", "MEDUSA_P2_GATE_OK", "MEDUSA_P3_GATE_OK"]:
            self.assertNotIn(f"grep -q '{legacy}' server.log", workflow)


if __name__ == "__main__":
    unittest.main()
