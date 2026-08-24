import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/fallen_knight/data/fallen_knight/function"
DP = ROOT / "datapacks/fallen_knight/data/fallen_knight"

class BossContractTests(unittest.TestCase):
    def text(self, rel):
        return (FN / rel).read_text(encoding="utf-8")

    def test_carrier_is_vindicator_and_has_no_free_damage(self):
        text = self.text("debug/spawn_carrier.mcfunction") + self.text("boss/bootstrap.mcfunction")
        self.assertIn("minecraft:vindicator", text)
        self.assertIn("minecraft:attack_damage base set 0", text)
        self.assertIn("minecraft:scale base set 1.4", text)

    def test_bootstrap_marks_boss_and_disables_vanilla_drops(self):
        text = self.text("boss/bootstrap.mcfunction")
        self.assertIn("tag @s add fk.boss", text)
        self.assertIn("DeathLootTable:\"fallen_knight:entity/empty\"", text)
        self.assertIn("weapon.mainhand with minecraft:iron_sword", text)
        self.assertIn("weapon.offhand with minecraft:shield", text)

    def test_director_prevents_immediate_repeat(self):
        director = self.text("boss/director/phase1.mcfunction")
        close = self.text("boss/director/phase1_close.mcfunction")
        mid = self.text("boss/director/phase1_mid.mcfunction")
        self.assertIn("scoreboard players random @s fk_roll", director)
        self.assertIn("scores={fk_prev=", close)
        self.assertIn("scores={fk_prev=", mid)

    def test_health_snapshot_is_available_for_later_phase_transition(self):
        text = self.text("boss/tick_one.mcfunction")
        self.assertIn("store result score @s fk_hp run data get entity @s Health", text)

    def test_heavy_damage_bypasses_shields(self):
        heavy = (DP / "damage_type/heavy.json").read_text(encoding="utf-8")
        tag = (ROOT / "datapacks/fallen_knight/data/minecraft/tags/damage_type/bypasses_shield.json").read_text(encoding="utf-8")
        self.assertIn("fallen_knight:heavy", tag)
        self.assertIn("mob", heavy)

    def test_phase1_attacks_are_separate_modules(self):
        attacks = ["guard", "bash", "combo", "overhead", "charge"]
        for attack in attacks:
            self.assertTrue((FN / f"boss/attack/{attack}/start.mcfunction").exists())
            self.assertTrue((FN / f"boss/attack/{attack}/tick.mcfunction").exists())

    def test_phase1_timing_contracts_are_encoded(self):
        guard = self.text("boss/attack/guard/tick.mcfunction")
        bash = self.text("boss/attack/bash/tick.mcfunction")
        combo = self.text("boss/attack/combo/tick.mcfunction")
        overhead = self.text("boss/attack/overhead/tick.mcfunction")
        charge = self.text("boss/attack/charge/tick.mcfunction")
        self.assertIn("fk_timer=20", guard)
        self.assertIn("fk_timer=7", bash)
        self.assertIn("fk_timer=5", combo)
        self.assertIn("fk_timer=10", overhead)
        self.assertIn("fk_timer=12", charge)

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
        wave = "\n".join([self.text("boss/attack/cursed_slash/spawn_wave.mcfunction"), self.text("boss/attack/cursed_slash/bootstrap_wave.mcfunction")])
        blades = "\n".join([self.text("boss/attack/spectral_blades/spawn.mcfunction"), self.text("boss/attack/spectral_blades/spawn_for_arena.mcfunction"), self.text("boss/attack/spectral_blades/spawn_one.mcfunction"), self.text("boss/attack/spectral_blades/bootstrap_one.mcfunction")])
        self.assertIn("fk.wave", wave)
        self.assertIn("fk_aid", wave)
        self.assertIn("fk.spectral", blades)
        self.assertIn("fk_aid", blades)

if __name__ == "__main__":
    unittest.main()
