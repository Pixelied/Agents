import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/fallen_knight/data/fallen_knight/function"

class BossContractTests(unittest.TestCase):
    def text(self, rel):
        return (FN / rel).read_text(encoding="utf-8")

    def test_carrier_is_vindicator_and_has_no_free_damage(self):
        spawn = self.text("debug/spawn_carrier.mcfunction")
        boot = self.text("boss/bootstrap.mcfunction")
        self.assertIn("minecraft:vindicator", spawn)
        self.assertIn("minecraft:attack_damage base set 0", boot)
        self.assertIn("minecraft:max_health base set 160", boot)
        self.assertIn("minecraft:scale base set 1.4", boot)

    def test_bootstrap_marks_boss_and_disables_vanilla_drops(self):
        boot = self.text("boss/bootstrap.mcfunction")
        self.assertIn("tag @s add fk.boss", boot)
        self.assertIn("fallen_knight:entity/empty", boot)

    def test_phase1_attacks_are_separate_modules(self):
        attacks = ["guard", "shield_bash", "knights_combo", "overhead", "charge"]
        for attack in attacks:
            self.assertTrue((FN / f"boss/attack/{attack}/start.mcfunction").exists())
            self.assertTrue((FN / f"boss/attack/{attack}/tick.mcfunction").exists())

    def test_director_prevents_immediate_repeat(self):
        text = self.text("boss/director/select_phase1.mcfunction")
        self.assertIn("fk_prev", text)
        self.assertIn("fk_roll", text)

    def test_health_snapshot_is_available_for_later_phase_transition(self):
        text = self.text("boss/tick_one.mcfunction")
        self.assertIn("fk_hp", text)

    def test_phase1_timing_contracts_are_encoded(self):
        guard = self.text("boss/attack/guard/tick.mcfunction")
        bash = self.text("boss/attack/shield_bash/tick.mcfunction")
        combo = self.text("boss/attack/knights_combo/tick.mcfunction")
        overhead = self.text("boss/attack/overhead/tick.mcfunction")
        charge = self.text("boss/attack/charge/tick.mcfunction")
        self.assertIn("fk_timer matches 8..27", guard)
        self.assertIn("fk_timer matches 10", bash)
        self.assertIn("fk_timer matches 30", combo)
        self.assertIn("fk_timer matches 19", overhead)
        self.assertIn("fk_timer matches 13..24", charge)

    def test_heavy_damage_bypasses_shields(self):
        path = ROOT / "datapacks/fallen_knight/data/minecraft/tags/damage_type/bypasses_shield.json"
        self.assertIn("fallen_knight:knight_heavy", path.read_text(encoding="utf-8"))

if __name__ == "__main__":
    unittest.main()
