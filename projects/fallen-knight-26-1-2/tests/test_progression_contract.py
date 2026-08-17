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

if __name__ == "__main__":
    unittest.main()
