from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class MazeDatapackContract(unittest.TestCase):
    def test_proposal_has_bounded_retry_and_delta_gate(self):
        propose = (FN / "maze/propose/start.mcfunction").read_text()
        accept = (FN / "maze/validate/accept.mcfunction").read_text()
        reject = (FN / "maze/validate/reject.mcfunction").read_text()
        self.assertIn("md_mtry", propose)
        self.assertIn("16", accept)
        self.assertIn("28", accept)
        self.assertIn("64", reject)

    def test_validation_is_time_sliced(self):
        tick = (FN / "maze/validate/tick.mcfunction").read_text()
        self.assertIn("function medusa:maze/validate/spread", tick)
        self.assertNotIn("schedule function medusa:maze/validate/tick 0t", tick)

    def test_next_edges_are_mirrored(self):
        mutate = (FN / "maze/propose/mutate_cell.mcfunction").read_text()
        for token in ["md_ne", "md_nw", "md_ns", "md_nn"]:
            self.assertIn(token, mutate)

    def test_shift_opens_before_it_closes(self):
        start_open = (FN / "maze/transition/start_open.mcfunction").read_text()
        open_tick = (FN / "maze/transition/open_tick.mcfunction").read_text()
        self.assertIn("start_close", open_tick)
        self.assertNotIn("start_close", start_open)

    def test_only_survival_or_adventure_players_drive_activity(self):
        text = (FN / "maze/activity/check_players.mcfunction").read_text()
        self.assertIn("gamemode=survival", text)
        self.assertIn("gamemode=adventure", text)
        self.assertNotIn("gamemode=creative", text)

    def test_warning_precedes_wall_transition(self):
        warning = (FN / "maze/warning/tick.mcfunction").read_text()
        self.assertIn("playsound", warning)
        self.assertIn("start_open", warning)

    def test_moving_walls_use_display_interpolation_and_real_collision(self):
        spawn = (FN / "maze/wall/spawn_display.mcfunction").read_text()
        for token in [
            "minecraft:block_display",
            "interpolation_duration",
            "start_interpolation",
            "transformation",
            "translation",
            "left_rotation",
            "scale",
            "right_rotation",
            "md_eid",
        ]:
            self.assertIn(token, spawn)
        close = (FN / "maze/wall/close_tick.mcfunction").read_text()
        self.assertIn("minecraft:barrier", close)
        self.assertIn("check_occupied", close)
        self.assertIn("abort_close", close)

    def test_generic_wall_abort_keeps_the_edge_open(self):
        abort = (FN / "maze/wall/abort_close.mcfunction").read_text()
        for token in ["md_ne", "md_nw", "md_ns", "md_nn"]:
            self.assertIn(token, abort)
        self.assertNotIn(" damage ", abort)

    def test_wall_cleanup_is_instance_scoped(self):
        cleanup = (FN / "maze/wall/cleanup.mcfunction").read_text()
        self.assertIn("md.maze.wall_display", cleanup)
        self.assertIn("md_eid=$(eid)", cleanup)


if __name__ == "__main__":
    unittest.main()
