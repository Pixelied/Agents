import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/medusa/data/medusa"

class RewardContract(unittest.TestCase):
    def test_staff_recipe_has_approved_counts(self):
        recipe = DP / "recipe/medusa_staff.json"
        self.assertTrue(recipe.is_file(), "Medusa Staff recipe is missing")
        text = recipe.read_text()
        for token in ["medusa_heart", "gorgon_scale", "serpent_fang", "netherite_ingot", "breeze_rod"]:
            self.assertIn(token, text)
        self.assertIn('"charges": 64', text)
        self.assertIn('"pattern": ["SHS", "SNF", "SB "]', text.replace("\n", " "))

    def test_kill_reward_has_required_materials_and_ranges(self):
        reward = DP / "loot_table/rewards/medusa_kill.json"
        self.assertTrue(reward.is_file(), "Medusa kill loot table is missing")
        text = reward.read_text()
        for token in ["medusa_heart", "gorgon_scale", "serpent_fang", "diamond", "gold_ingot", "netherite_scrap"]:
            self.assertIn(token, text)
        self.assertIn("10", text)
        self.assertIn("14", text)
        self.assertIn("0.25", text)
