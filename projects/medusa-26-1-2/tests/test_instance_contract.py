from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"

class InstanceContract(unittest.TestCase):
    def test_instance_dispatch_exists(self):
        self.assertTrue((FN / "instance/register.mcfunction").is_file())
        self.assertTrue((FN / "instance/recover_loaded.mcfunction").is_file())
        tick_one = FN / "instance/tick_one.mcfunction"
        self.assertTrue(tick_one.is_file(), "instance/tick_one.mcfunction is missing")
        self.assertIn("md_eid", tick_one.read_text())

    def test_registration_assigns_authoritative_instance_id(self):
        text = (FN / "instance/register.mcfunction").read_text()
        self.assertIn("$next_eid", text)
        self.assertIn("md_eid", text)
        self.assertIn("function medusa:dungeon/build_generated", text)
        self.assertIn("medusa:macro", text)

    def test_participant_cleanup_is_explicit(self):
        clear = FN / "instance/participants/clear_player.mcfunction"
        self.assertTrue(clear.is_file(), "participant cleanup function is missing")
        text = clear.read_text()
        self.assertIn("md_petr", text)
        self.assertIn("md.participant", text)

class GoldenEyeContract(unittest.TestCase):
    EYE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ1NzdkOWU1YTVhZGM4ZTA5MzYyOTVlYjYzMDBmZGUwZmY5YjAyM2YyMGJlZmMxNTNiMjhkZWVlYTgwNDdhMSJ9fX0="

    def test_eye_item_uses_approved_texture_and_clean_lore(self):
        path = ROOT / "datapacks/medusa/data/medusa/loot_table/items/golden_gorgon_eye.json"
        self.assertTrue(path.is_file(), "Golden Gorgon Eye loot table is missing")
        text = path.read_text()
        self.assertIn(self.EYE, text)
        self.assertIn("golden_gorgon_eye", text)
        self.assertNotIn("minecraft-heads.com", text)

    def test_awakening_has_start_tick_finish(self):
        for rel in ["arena/awakening/start.mcfunction", "arena/awakening/tick.mcfunction", "arena/awakening/finish.mcfunction"]:
            self.assertTrue((FN / rel).is_file(), f"missing awakening function: {rel}")
