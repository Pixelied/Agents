from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/medusa"
FN = DP / "data/medusa/function"

class StaffInputContract(unittest.TestCase):
    def test_using_item_event_and_charge_functions(self):
        use_event = DP / "data/medusa/advancement/events/staff_using.json"
        recharge = FN / "staff/recharge.mcfunction"
        write = FN / "staff/write_charges.mcfunction"
        self.assertTrue(use_event.is_file(), "Staff using advancement is missing")
        self.assertTrue(recharge.is_file(), "Staff recharge function is missing")
        self.assertTrue(write.is_file(), "Staff charge writer is missing")
        self.assertIn("minecraft:using_item", use_event.read_text())
        self.assertIn("8", recharge.read_text())
        self.assertIn("64", write.read_text())

    def test_first_normal_use_spends_one_charge_and_quick_pulses(self):
        start = FN / "staff/start_use.mcfunction"
        pulse = FN / "staff/quick_pulse.mcfunction"
        self.assertTrue(start.is_file(), "Staff start-use function is missing")
        self.assertTrue(pulse.is_file(), "Staff quick pulse is missing")
        text = start.read_text()
        self.assertIn("remove @s md_staff 1", text)
        self.assertIn("function medusa:staff/quick_pulse", text)

class StaffChargeSafetyContract(unittest.TestCase):
    def test_zero_charge_staff_cannot_quick_pulse(self):
        text = (FN / "staff/start_use.mcfunction").read_text()
        self.assertIn("scoreboard players set @s md_tmp 0", text)
        self.assertIn("if score @s md_staff matches 1.. run scoreboard players set @s md_tmp 1", text)
        self.assertNotIn("matches 0..63 run function medusa:staff/quick_pulse", text)
