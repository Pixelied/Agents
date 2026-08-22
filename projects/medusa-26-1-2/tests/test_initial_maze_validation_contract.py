from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class InitialMazeValidationContract(unittest.TestCase):
    def test_initial_topology_is_validated_before_materialization(self):
        load = (FN / "load.mcfunction").read_text()
        self.assertIn("scoreboard objectives add md_mgen_try dummy", load)
        loops = (FN / "maze/generate/add_loops.mcfunction").read_text()
        self.assertIn("medusa:maze/generate/validate_initial/start", loops)
        self.assertNotIn("medusa:maze/materialize/start", loops)
        accept = FN / "maze/generate/validate_initial/accept.mcfunction"
        reject = FN / "maze/generate/validate_initial/reject.mcfunction"
        self.assertTrue(accept.is_file())
        self.assertTrue(reject.is_file())
        self.assertIn("medusa:maze/materialize/start", accept.read_text())
        self.assertIn("md_mgen_try", reject.read_text())

    def test_initial_validation_has_bounded_tree_fallback(self):
        loops = (FN / "maze/generate/add_loops.mcfunction").read_text()
        self.assertIn("md_mgen_try", loops)
        self.assertIn("16", loops)
        reject = (FN / "maze/generate/validate_initial/reject.mcfunction").read_text()
        self.assertIn("16", reject)

    def test_maze_ready_waiter_is_one_shot_after_smoke_starts(self):
        loaded = (FN / "debug/create_test_temple_loaded.mcfunction").read_text()
        waiter = (FN / "debug/wait_for_maze_ready.mcfunction").read_text()
        self.assertIn("$maze_smoke_started", loaded)
        self.assertIn("$maze_smoke_started", waiter)
        self.assertIn("matches 0", waiter)

    def test_initial_smoke_checks_rejection_before_success_continuation(self):
        tick = (FN / "debug/maze_smoke/initial_tick.mcfunction").read_text()
        reject = tick.find("md_mphase matches 3 run say MEDUSA_MAZE_INITIAL_SOLVABLE_FAILED")
        success = tick.find("md_mphase matches 5 run function medusa:debug/maze_smoke/initial_pass")
        self.assertGreaterEqual(reject, 0)
        self.assertGreaterEqual(success, 0)
        self.assertLess(
            reject,
            success,
            "the successful initial_pass starts proposal phase 3, so the rejection check must run first",
        )

    def test_headless_occupied_close_probe_is_debug_gated(self):
        check = (FN / "maze/wall/check_occupied.mcfunction").read_text()
        self.assertIn("gamemode=survival", check)
        self.assertIn("gamemode=adventure", check)
        self.assertIn("tag=md.debug_occupied_controller", check)
        self.assertIn("md.maze.occupancy_probe", check)

    def test_exact_runtime_disables_empty_server_pause(self):
        workflow = (REPO / ".github/workflows/medusa-26-1-2-ci.yml").read_text()
        self.assertIn("pause-when-empty-seconds=-1", workflow)


if __name__ == "__main__":
    unittest.main()
