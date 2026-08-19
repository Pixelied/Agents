import gzip
import json
import pathlib
import struct
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/fallen_knight/data/fallen_knight"
MC = ROOT / "datapacks/fallen_knight/data/minecraft"


def nbt_root_names(path):
    raw = gzip.open(path, "rb").read()
    pos = 0
    def u8():
        nonlocal pos
        v = raw[pos]; pos += 1; return v
    def i16u():
        nonlocal pos
        v = struct.unpack_from(">H", raw, pos)[0]; pos += 2; return v
    def string():
        nonlocal pos
        n = i16u(); v = raw[pos:pos+n].decode(); pos += n; return v
    def skip(t):
        nonlocal pos
        if t == 1: pos += 1
        elif t == 2: pos += 2
        elif t == 3: pos += 4
        elif t == 4: pos += 8
        elif t == 5: pos += 4
        elif t == 6: pos += 8
        elif t == 7: n = struct.unpack_from(">i", raw, pos)[0]; pos += 4 + n
        elif t == 8: string()
        elif t == 9:
            et = u8(); n = struct.unpack_from(">i", raw, pos)[0]; pos += 4
            for _ in range(n): skip(et)
        elif t == 10:
            while True:
                tt = u8()
                if tt == 0: break
                string(); skip(tt)
        elif t == 11: n = struct.unpack_from(">i", raw, pos)[0]; pos += 4 + 4*n
        elif t == 12: n = struct.unpack_from(">i", raw, pos)[0]; pos += 4 + 8*n
        else: raise AssertionError(t)
    self_type = u8(); self_name = string()
    if self_type != 10 or self_name != "": raise AssertionError("not unnamed root compound")
    names = []
    while True:
        t = u8()
        if t == 0: break
        name = string(); names.append(name); skip(t)
    return names


class WorldgenContractTests(unittest.TestCase):
    def load(self, rel):
        return json.loads((DP / rel).read_text(encoding="utf-8"))

    def test_castle_worldgen_is_registered_and_rare(self):
        structure = self.load("worldgen/structure/fallen_castle.json")
        self.assertEqual(structure["type"], "minecraft:jigsaw")
        self.assertEqual(structure["start_pool"], "fallen_knight:castle/start")
        self.assertEqual(structure["biomes"], "#fallen_knight:has_structure/fallen_castle")
        placement = self.load("worldgen/structure_set/fallen_castles.json")["placement"]
        self.assertEqual((placement["spacing"], placement["separation"]), (80, 40))

    def test_castle_template_and_map_tag_exist(self):
        pool = self.load("worldgen/template_pool/castle/start.json")
        self.assertEqual(pool["elements"][0]["element"]["location"], "fallen_knight:castle/main")
        tag = self.load("tags/worldgen/structure/castle.json")
        self.assertIn("fallen_knight:fallen_castle", tag["values"])
        nbt = DP / "structure/castle/main.nbt"
        self.assertTrue(nbt.exists())
        self.assertTrue({"size", "palette", "blocks", "entities", "DataVersion"}.issubset(nbt_root_names(nbt)))

    def test_clues_are_more_common_and_have_two_templates(self):
        placement = self.load("worldgen/structure_set/clue_ruins.json")["placement"]
        self.assertEqual((placement["spacing"], placement["separation"]), (32, 16))
        pool = self.load("worldgen/template_pool/clue/start.json")
        locations = {e["element"]["location"] for e in pool["elements"]}
        self.assertEqual(locations, {"fallen_knight:clue/camp", "fallen_knight:clue/watchtower"})
        for name in ("camp", "watchtower"):
            nbt = DP / f"structure/clue/{name}.nbt"
            self.assertTrue(nbt.exists())
            self.assertIn("DataVersion", nbt_root_names(nbt))

    def test_castle_loot_and_clue_loot_exist(self):
        castle = self.load("loot_table/chests/castle.json")
        clue = self.load("loot_table/chests/clue.json")
        self.assertTrue(castle["pools"])
        self.assertTrue(clue["pools"])
        self.assertNotIn("oathbreaker", json.dumps(castle))
        self.assertNotIn("cursed_sword_fragment", json.dumps(castle))

    def test_cartographer_level3_trade_targets_castle_tag(self):
        trade = self.load("villager_trade/castle_map.json")
        self.assertEqual(trade["wants"]["count"], 16)
        self.assertEqual(trade["additional_wants"]["id"], "minecraft:compass")
        maps = [f for f in trade["given_item_modifiers"] if f["function"] == "minecraft:exploration_map"]
        self.assertEqual(maps[0]["destination"], "fallen_knight:castle")
        tag = json.loads((MC / "tags/villager_trade/cartographer/level_3.json").read_text(encoding="utf-8"))
        self.assertFalse(tag.get("replace", False))
        self.assertIn("fallen_knight:castle_map", tag["values"])


if __name__ == "__main__": unittest.main()
