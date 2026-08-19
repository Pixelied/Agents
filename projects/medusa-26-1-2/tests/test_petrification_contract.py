from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"

class GazeContract(unittest.TestCase):
    def test_exact_gaze_rates(self):
        normal = FN / "gaze/apply_normal.mcfunction"
        gorgon = FN / "gaze/apply_gorgon.mcfunction"
        decay = FN / "gaze/decay.mcfunction"
        self.assertTrue(normal.is_file(), "normal gaze function is missing")
        self.assertTrue(gorgon.is_file(), "Gorgon Gaze function is missing")
        self.assertTrue(decay.is_file(), "gaze decay function is missing")
        self.assertIn("add @s md_petr 12", normal.read_text())
        self.assertIn("add @s md_petr 55", gorgon.read_text())
        self.assertIn("remove @s md_petr 20", decay.read_text())

    def test_angle_and_los_contract_exists(self):
        angle = FN / "gaze/check_angle.mcfunction"
        ray = FN / "gaze/los_ray.mcfunction"
        self.assertTrue(angle.is_file(), "angle check is missing")
        self.assertTrue(ray.is_file(), "LOS ray is missing")
        self.assertIn("distance=..0.44", angle.read_text())
        self.assertIn("#medusa:gaze_passable", ray.read_text())

    def test_gaze_diagnostics_distinguish_angle_from_los_failure(self):
        toggle = FN / "debug/toggle_gaze_diagnostics.mcfunction"
        angle_hit = FN / "gaze/angle_hit.mcfunction"
        los_hit = FN / "gaze/los_hit.mcfunction"
        check = FN / "gaze/check_player.mcfunction"
        ui = FN / "gaze/ui.mcfunction"
        load = FN / "load.mcfunction"

        self.assertTrue(toggle.is_file(), "gaze diagnostic toggle is missing")
        self.assertTrue(angle_hit.is_file(), "angle diagnostic boundary helper is missing")
        self.assertIn("md_angle_ok", load.read_text())
        self.assertIn("md_los_ok", load.read_text())
        self.assertIn("scoreboard players set @s md_angle_ok 0", check.read_text())
        self.assertIn("scoreboard players set @s md_los_ok 0", check.read_text())
        self.assertIn("scoreboard players set @s md_angle_ok 1", angle_hit.read_text())
        self.assertIn("scoreboard players set @s md_los_ok 1", los_hit.read_text())
        self.assertIn("md.gaze_debug", ui.read_text())
        self.assertIn("ANGLE", ui.read_text())
        self.assertIn("LOS", ui.read_text())

    def test_thresholds_reach_full_petrification_pending_state(self):
        thresholds = FN / "gaze/apply_thresholds.mcfunction"
        self.assertTrue(thresholds.is_file(), "threshold function is missing")
        text = thresholds.read_text()
        self.assertIn("400..699", text)
        self.assertIn("700..899", text)
        self.assertIn("900..999", text)
        self.assertIn("1000..", text)

class RescueContract(unittest.TestCase):
    def test_rescue_progress_weights_and_grace(self):
        hit = FN / "petrify/rescue/apply_hit.mcfunction"
        free = FN / "petrify/break_free.mcfunction"
        self.assertTrue(hit.is_file(), "rescue hit function is missing")
        self.assertTrue(free.is_file(), "break-free function is missing")
        text = hit.read_text()
        self.assertIn("md_shell", text)
        self.assertIn("4", text)
        self.assertIn("2", text)
        self.assertIn("1", text)
        self.assertIn("md_grace", free.read_text())

    def test_full_petrification_has_custom_suffocation(self):
        enter = FN / "petrify/enter_full.mcfunction"
        suffocate = FN / "petrify/suffocate.mcfunction"
        self.assertTrue(enter.is_file(), "full petrification entrypoint is missing")
        self.assertTrue(suffocate.is_file(), "suffocation function is missing")
        self.assertIn("md.petrified", enter.read_text())
        self.assertIn("damage @s 2 medusa:petrification", suffocate.read_text())
