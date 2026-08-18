import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/medusa/data/medusa"


class RewardContract(unittest.TestCase):
    def test_staff_recipe_has_approved_counts(self):
        recipe = DP / "recipe/medusa_staff.json"
        advancement = DP / "advancement/events/medusa_staff_crafted.json"
        staff_loot = DP / "loot_table/items/medusa_staff.json"
        self.assertTrue(recipe.is_file(), "Medusa Staff recipe is missing")
        self.assertTrue(advancement.is_file(), "Medusa Staff validation advancement is missing")
        recipe_text = recipe.read_text()
        validation_text = advancement.read_text()
        for token in ["netherite_ingot", "breeze_rod"]:
            self.assertIn(token, recipe_text)
        for token in ["medusa_heart", "gorgon_scale", "serpent_fang"]:
            self.assertIn(token, validation_text)
        self.assertIn('"pattern": ["SHS", "SNF", "SB "]', recipe_text.replace("\n", " "))
        self.assertIn('"charges": 64', staff_loot.read_text())

    def test_staff_recipe_validates_all_custom_boss_materials_before_activation(self):
        recipe = (DP / "recipe/medusa_staff.json").read_text()
        self.assertIn("medusa_staff_candidate", recipe)
        self.assertNotIn('"md_item": "medusa_staff", "charges": 64', recipe)

        advancement = DP / "advancement/events/medusa_staff_crafted.json"
        self.assertTrue(advancement.is_file(), "missing component-validation recipe_crafted advancement")
        text = advancement.read_text()
        self.assertIn("minecraft:recipe_crafted", text)
        self.assertIn("medusa:medusa_staff", text)
        self.assertIn("medusa_heart", text)
        self.assertGreaterEqual(text.count("gorgon_scale"), 4)
        self.assertIn("serpent_fang", text)

        activate = DP / "function/staff/craft/activate.mcfunction"
        self.assertTrue(activate.is_file(), "missing valid-craft activation function")
        activate_text = activate.read_text()
        self.assertIn("medusa_staff_candidate", activate_text)
        self.assertIn("medusa:items/medusa_staff", activate_text)

    def test_kill_reward_has_required_materials_and_ranges(self):
        reward = DP / "loot_table/rewards/medusa_kill.json"
        self.assertTrue(reward.is_file(), "Medusa kill loot table is missing")
        text = reward.read_text()
        for token in ["medusa_heart", "gorgon_scale", "serpent_fang", "diamond", "gold_ingot", "netherite_scrap"]:
            self.assertIn(token, text)
        self.assertIn("10", text)
        self.assertIn("14", text)
        self.assertIn("0.25", text)
