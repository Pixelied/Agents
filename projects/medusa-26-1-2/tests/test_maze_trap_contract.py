from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class MazeTrapContract(unittest.TestCase):
    def test_all_required_trap_families_exist(self):
        for rel in [
            "serpent_nest",
            "venom_gallery",
            "lava_fissure",
            "crusher",
            "gorgon_relief",
            "drop_route",
            "expedition",
        ]:
            path = FN / f"maze/trap/{rel}/start.mcfunction"
            self.assertTrue(path.is_file(), f"missing maze trap family: {rel}")

    def test_traps_are_instance_scoped_and_bounded_to_four(self):
        setup = (FN / "maze/trap/setup.mcfunction").read_text()
        rearm = (FN / "maze/trap/rearm_ctx.mcfunction").read_text()
        cleanup = (FN / "maze/trap/cleanup_ctx.mcfunction").read_text()
        self.assertIn("md_eid", setup)
        self.assertIn("limit=4", rearm)
        self.assertIn("md_eid=$(eid)", rearm)
        self.assertIn("md_eid=$(eid)", cleanup)

    def test_generic_trap_controller_has_telegraph_before_hazard(self):
        tick = (FN / "maze/trap/tick_one.mcfunction").read_text()
        self.assertIn("md_marmed", tick)
        self.assertIn("md_mtrap_timer", tick)
        for rel in ["crusher", "gorgon_relief", "venom_gallery"]:
            start = (FN / f"maze/trap/{rel}/start.mcfunction").read_text()
            self.assertIn("playsound", start)
            self.assertIn("particle", start)

    def test_trap_runtime_is_connected_to_stable_maze_and_rearms_after_shift(self):
        maze_tick = (FN / "maze/tick.mcfunction").read_text()
        commit = (FN / "maze/transition/commit.mcfunction").read_text()
        self.assertIn("medusa:maze/trap/tick", maze_tick)
        self.assertIn("medusa:maze/trap/rearm", commit)


if __name__ == "__main__":
    unittest.main()
