import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/fallen_knight/data/fallen_knight"
FN = DP / "function"

class ProgressionContractTests(unittest.TestCase):
    def text(self, rel):
        return (FN / rel).read_text(encoding="utf-8")

    def test_rewards_require_200_ticks(self):
        self.assertIn("fk_ptime=200..", self.text("reward/distribute_for_arena.mcfunction"))

    def test_pending_rewards_are_keyed_by_encounter(self):
        pending = self.text("reward/pending_for_eid.mcfunction")
        self.assertIn("fk_win_$(eid)", pending)
        self.assertIn("fk_result matches 1", pending)

    def test_required_progression_is_given_per_player(self):
        first = self.text("reward/first.mcfunction")
        self.assertIn("loot give @s loot fallen_knight:rewards/first_clear", first)
        self.assertIn("scoreboard players set @s fk_first 1", first)

    def test_cleave_has_charge_proxy_and_internal_cooldown(self):
        hit = self.text("item/player_hurt_entity.mcfunction")
        self.assertIn("fk_swing=16..", hit)
        self.assertIn("fk_cleave=0", hit)
        self.assertIn("fk_cleave 100", self.text("item/cleave.mcfunction"))

    def test_first_clear_loot_has_oathbreaker_and_fragment(self):
        data = json.loads((DP / "loot_table/rewards/first_clear.json").read_text(encoding="utf-8"))
        text = json.dumps(data)
        self.assertIn('oathbreaker', text)
        self.assertIn('cursed_sword_fragment', text)

    def test_oathbreaker_attribute_modifiers_use_26_1_array_schema(self):
        data = json.loads((DP / "loot_table/rewards/first_clear.json").read_text(encoding="utf-8"))
        components = data["pools"][0]["entries"][0]["functions"][0]["components"]
        self.assertIsInstance(components["minecraft:attribute_modifiers"], list)

    def test_repeat_clear_does_not_guarantee_fragment(self):
        data = json.loads((DP / "loot_table/rewards/repeat_clear.json").read_text(encoding="utf-8"))
        self.assertNotIn('cursed_sword_fragment', json.dumps(data))

    def test_hit_event_is_rearmable(self):
        hook = self.text("item/player_hurt_entity.mcfunction")
        self.assertIn("advancement revoke @s only fallen_knight:events/player_hurt_entity", hook)

    def test_knights_oath_recipe_requires_fragment_placeholder(self):
        recipe = json.loads((DP / "recipe/knights_oath.json").read_text(encoding="utf-8"))
        self.assertEqual(recipe["key"]["F"], "minecraft:structure_void")
        self.assertEqual(recipe["result"]["components"]["minecraft:custom_data"]["fk_item"], "knights_oath")
        self.assertEqual(recipe["result"]["components"]["minecraft:max_stack_size"], 1)

    def test_first_clear_unlocks_knights_oath_recipe(self):
        first = self.text("reward/first.mcfunction")
        self.assertIn("recipe give @s fallen_knight:knights_oath", first)

    def test_ritual_requires_cleared_arena_and_exact_offering(self):
        on_use = self.text("ritual/on_use.mcfunction")
        check = self.text("ritual/check_offering.mcfunction")
        activate = self.text("ritual/activate.mcfunction")
        self.assertIn("scores={fk_state=2}", on_use)
        self.assertIn("clear @s minecraft:diamond 0", check)
        self.assertIn("clear @s minecraft:soul_sand 0", check)
        self.assertIn("clear @s minecraft:iron_ingot 0", check)
        self.assertIn("matches 1..", check)
        self.assertIn("matches 4..", check)
        self.assertIn("clear @s minecraft:diamond 1", activate)
        self.assertIn("clear @s minecraft:soul_sand 4", activate)
        self.assertIn("clear @s minecraft:iron_ingot 4", activate)
        self.assertIn("function fallen_knight:arena/rematch_spawn", activate)

    def test_oath_use_regrants_relic_before_validation(self):
        on_use = self.text("ritual/on_use.mcfunction")
        loot_line = "loot give @s loot fallen_knight:items/knights_oath"
        self.assertIn(loot_line, on_use)
        self.assertLess(on_use.index(loot_line), on_use.index("fk_state=2"))
        oath = json.loads((DP / "loot_table/items/knights_oath.json").read_text(encoding="utf-8"))
        text = json.dumps(oath)
        self.assertIn('"minecraft:max_stack_size": 1', text)
        self.assertIn('"fk_item": "knights_oath"', text)

    def test_rematch_spawn_reuses_arena_and_starts_immediately(self):
        rematch = self.text("arena/rematch_spawn.mcfunction")
        self.assertIn("fk_clear matches 1", rematch)
        self.assertIn("fk_state matches 2", rematch)
        self.assertIn("function fallen_knight:arena/spawn_dormant_boss", rematch)
        self.assertIn("function fallen_knight:arena/start", rematch)

if __name__ == "__main__":
    unittest.main()
