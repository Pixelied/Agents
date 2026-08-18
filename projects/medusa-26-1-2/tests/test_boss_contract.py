from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"

class BossContract(unittest.TestCase):
    def test_husk_carrier_and_instance_tag(self):
        boot = FN / "boss/bootstrap.mcfunction"
        self.assertTrue(boot.is_file())
        text = boot.read_text()
        self.assertIn("minecraft:husk", text)
        self.assertIn("md.boss", text)
        self.assertIn("minecraft:attack_damage", text)

    def test_health_scaling_contains_solo_and_cap(self):
        scale = FN / "boss/health/apply_scale.mcfunction"
        self.assertTrue(scale.is_file(), "boss health scaling function is missing")
        text = scale.read_text()
        self.assertIn("300", text)
        self.assertIn("600", text)
        self.assertIn("75", text)

    def test_three_phase_transition_functions_exist(self):
        for rel in [
            "boss/transition/start_phase2.mcfunction",
            "boss/transition/start_phase3.mcfunction",
            "boss/transition/tick.mcfunction",
            "boss/transition/finish.mcfunction",
        ]:
            self.assertTrue((FN / rel).is_file(), f"missing transition function: {rel}")

class AttackSuiteContract(unittest.TestCase):
    def test_required_attack_pool_exists(self):
        attack = FN / "boss/attack"
        for rel in [
            "claw/start.mcfunction",
            "serpent_lash/start.mcfunction",
            "venom_spit/start.mcfunction",
            "brood_call/start.mcfunction",
            "gorgon_gaze/start.mcfunction",
            "large_serpent/start.mcfunction",
        ]:
            self.assertTrue((attack / rel).is_file(), f"missing Medusa attack: {rel}")

    def test_gorgon_gaze_has_telegraph_and_empowered_window(self):
        tick = FN / "boss/attack/gorgon_gaze/tick.mcfunction"
        self.assertTrue(tick.is_file(), "Gorgon Gaze tick is missing")
        text = tick.read_text()
        self.assertIn("LOOK AWAY!", text)
        self.assertIn("35..84", text)
        self.assertIn("md_gorgon_active", text)
        self.assertIn("85..", text)
