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

if __name__ == "__main__":
    unittest.main()
