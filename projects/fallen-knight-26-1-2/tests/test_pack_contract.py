import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/fallen_knight"
RP = ROOT / "resourcepacks/fallen_knight"

class PackContractTests(unittest.TestCase):
    def load_json(self, path):
        with path.open(encoding="utf-8") as fh:
            return json.load(fh)

    def test_datapack_is_locked_to_101_1(self):
        pack = self.load_json(DP / "pack.mcmeta")["pack"]
        self.assertEqual(pack["min_format"], [101, 1])
        self.assertEqual(pack["max_format"], [101, 1])

    def test_resourcepack_is_locked_to_84_0(self):
        pack = self.load_json(RP / "pack.mcmeta")["pack"]
        self.assertEqual(pack["min_format"], [84, 0])
        self.assertEqual(pack["max_format"], [84, 0])

    def test_no_legacy_plural_registry_directories(self):
        banned = {"functions", "advancements", "recipes", "loot_tables", "structures"}
        offenders = [p for p in DP.rglob("*") if p.is_dir() and p.name in banned]
        self.assertEqual(offenders, [])

    def test_load_and_tick_tags_exist(self):
        load = self.load_json(DP / "data/minecraft/tags/function/load.json")
        tick = self.load_json(DP / "data/minecraft/tags/function/tick.json")
        self.assertIn("fallen_knight:load", load["values"])
        self.assertIn("fallen_knight:tick", tick["values"])

    def test_custom_item_models_resolve(self):
        names = ("oathbreaker", "cursed_sword_fragment", "broken_plate", "knights_oath")
        for name in names:
            item = self.load_json(RP / f"assets/fallen_knight/items/{name}.json")
            self.assertEqual(item["model"]["type"], "minecraft:model")
            self.assertEqual(item["model"]["model"], f"fallen_knight:item/{name}")
            self.assertTrue((RP / f"assets/fallen_knight/models/item/{name}.json").exists())

if __name__ == "__main__": unittest.main()
