from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class DungeonRuntimeContract(unittest.TestCase):
    def test_runtime_smoke_exercises_all_three_puzzle_gates(self):
        smoke = FN / "debug/test_dungeon_progression.mcfunction"
        instance_smoke = FN / "debug/test_dungeon_progression_instance.mcfunction"
        self.assertTrue(smoke.is_file(), "dungeon progression runtime smoke is missing")
        self.assertTrue(instance_smoke.is_file(), "instance-scoped dungeon progression smoke is missing")
        self.assertIn("function medusa:debug/test_dungeon_progression_instance", smoke.read_text())
        text = instance_smoke.read_text()
        for marker in [
            "MEDUSA_P1_GATE_OK",
            "MEDUSA_P2_GATE_OK",
            "MEDUSA_P3_GATE_OK",
            "MEDUSA_FINAL_GATE_OK",
            "MEDUSA_EYE_PRESENT_OK",
        ]:
            self.assertIn(marker, text)

        loaded = (FN / "debug/create_test_temple_loaded.mcfunction").read_text()
        self.assertIn("function medusa:debug/test_dungeon_progression", loaded)
        self.assertLess(
            loaded.find("function medusa:debug/test_dungeon_progression"),
            loaded.find("function medusa:debug/start_test_boss"),
            "dungeon progression must be exercised before the boss bypass smoke",
        )

    def test_ci_requires_dungeon_progression_markers(self):
        workflow = (REPO / ".github/workflows/medusa-26-1-2-ci.yml").read_text()
        for marker in [
            "MEDUSA_P1_GATE_OK",
            "MEDUSA_P2_GATE_OK",
            "MEDUSA_P3_GATE_OK",
            "MEDUSA_FINAL_GATE_OK",
            "MEDUSA_EYE_PRESENT_OK",
        ]:
            self.assertIn(f"grep -q '{marker}' server.log", workflow)


if __name__ == "__main__":
    unittest.main()
