import pathlib
import struct
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/fallen_knight/data/fallen_knight/function"
RP = ROOT / "resourcepacks/fallen_knight"
TEXTURE = RP / "assets/fallen_knight/textures/entity/fallen_knight.png"


class SkinVisualContractTests(unittest.TestCase):
    def text(self, rel):
        return (DP / rel).read_text(encoding="utf-8")

    def test_skin_one_is_bundled_as_64x64_png(self):
        raw = TEXTURE.read_bytes()
        self.assertTrue(raw.startswith(b"\x89PNG\r\n\x1a\n"))
        width, height = struct.unpack(">II", raw[16:24])
        self.assertEqual((width, height), (64, 64))

    def test_visual_uses_mannequin_resource_texture(self):
        spawn = self.text("visual/spawn.mcfunction")
        self.assertIn("minecraft:mannequin", spawn)
        self.assertIn('texture:\"fallen_knight:entity/fallen_knight\"', spawn)
        self.assertIn('model:\"wide\"', spawn)
        self.assertIn("fk.visual", spawn)
        self.assertIn("fk_aid", spawn)

    def test_carrier_is_hidden_and_visual_is_spawned_after_arena_id(self):
        bootstrap = self.text("boss/bootstrap.mcfunction")
        arena_spawn = self.text("arena/spawn_dormant_boss.mcfunction")
        debug_spawn = self.text("debug/spawn_carrier.mcfunction")
        self.assertIn("Invisible:1b", bootstrap)
        self.assertIn("function fallen_knight:visual/spawn", arena_spawn)
        self.assertGreater(arena_spawn.index("function fallen_knight:visual/spawn"), arena_spawn.index("fk_aid"))
        self.assertIn("function fallen_knight:visual/spawn", debug_spawn)

    def test_visual_tracks_matching_arena_and_equipment(self):
        tick = self.text("tick.mcfunction")
        sync = self.text("visual/sync_for_arena.mcfunction")
        self.assertIn("function fallen_knight:visual/tick_all", tick)
        self.assertGreater(tick.index("function fallen_knight:visual/tick_all"), tick.index("function fallen_knight:boss/tick_all"))
        self.assertIn("scores={fk_aid=$(aid)}", sync)
        self.assertIn("tp @s @e[tag=fk.boss", sync)
        self.assertIn("weapon.mainhand", sync)
        self.assertIn("weapon.offhand", sync)

    def test_visual_is_cleaned_up_with_boss(self):
        cleanup = self.text("arena/kill_boss_for_arena.mcfunction")
        self.assertIn("tag=fk.visual", cleanup)
        self.assertIn("fk_aid=$(aid)", cleanup)


if __name__ == "__main__":
    unittest.main()
