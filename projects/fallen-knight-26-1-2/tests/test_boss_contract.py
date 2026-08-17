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

    def test_phase2_attacks_are_separate_modules(self):
        attacks = ["sweep", "lunge", "cursed_slash", "heavy_combo", "slam", "spectral_blades"]
        for attack in attacks:
            self.assertTrue((FN / f"boss/attack/{attack}/start.mcfunction").exists())
            self.assertTrue((FN / f"boss/attack/{attack}/tick.mcfunction").exists())

    def test_transition_uses_half_health_threshold(self):
        text = self.text("boss/tick_one.mcfunction")
        self.assertIn("fk_hp <= @s fk_halfhp", text)
        self.assertIn("boss/transition/start", text)

    def test_transition_state_and_oathbroken_finish(self):
        start = self.text("boss/transition/start.mcfunction")
        finish = self.text("boss/transition/finish.mcfunction")
        self.assertIn("fk_phase 2", start)
        self.assertIn("fk_phase 3", finish)
        self.assertIn("weapon.offhand with minecraft:air", finish)

    def test_phase2_helpers_are_arena_scoped(self):
        wave = self.text("boss/attack/cursed_slash/spawn_wave.mcfunction")
        blades = "\n".join([self.text("boss/attack/spectral_blades/spawn.mcfunction"), self.text("boss/attack/spectral_blades/spawn_for_arena.mcfunction"), self.text("boss/attack/spectral_blades/spawn_one.mcfunction")])
        self.assertIn("fk.wave", wave)
        self.assertIn("fk_aid", wave)
        self.assertIn("fk.spectral", blades)
        self.assertIn("fk_aid", blades)

if __name__ == "__main__":
    unittest.main()
