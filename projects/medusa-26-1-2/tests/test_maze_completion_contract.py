from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class MazeCompletionContract(unittest.TestCase):
    def test_old_puzzles_are_removed_from_critical_path(self):
        tick = (FN / "instance/tick_one.mcfunction").read_text()
        self.assertNotIn("medusa:puzzle/", tick)
        load = (FN / "load.mcfunction").read_text()
        for obj in [
            "md_p1_done", "md_p2_done", "md_p3_done", "md_p1_o1",
            "md_p2_left", "md_p3_zone",
        ]:
            self.assertNotIn(obj, load)

    def test_sanctum_crossing_is_the_first_clear_gate(self):
        check = FN / "maze/completion/check.mcfunction"
        check_ctx = FN / "maze/completion/check_ctx.mcfunction"
        complete = FN / "maze/completion/complete.mcfunction"
        self.assertTrue(check.is_file(), "maze completion check is missing")
        self.assertTrue(check_ctx.is_file(), "instance-scoped maze completion check is missing")
        self.assertTrue(complete.is_file(), "maze completion handler is missing")
        check_text = check.read_text() + "\n" + check_ctx.read_text()
        self.assertIn("gamemode=survival", check_text)
        self.assertIn("gamemode=adventure", check_text)
        self.assertIn("md_eid=$(eid)", check_text)
        self.assertNotIn("gamemode=spectator", check_text)
        complete_text = complete.read_text()
        self.assertIn("md_dungeon_clear 1", complete_text)
        self.assertIn("md_mphase 9", complete_text)

    def test_locked_eye_feedback_points_to_labyrinth(self):
        text = (FN / "arena/pedestal/locked_feedback.mcfunction").read_text().lower()
        self.assertIn("labyrinth", text)
        self.assertNotIn("three trials", text)


if __name__ == "__main__":
    unittest.main()
