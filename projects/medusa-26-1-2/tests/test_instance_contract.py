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
