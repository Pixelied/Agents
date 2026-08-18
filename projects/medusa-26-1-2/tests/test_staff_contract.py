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

class StaffChannelContract(unittest.TestCase):
    def test_channel_timing_and_interrupt_lock_contract(self):
        channel = FN / "staff/channel/tick.mcfunction"
        interrupt = FN / "staff/channel/interrupt.mcfunction"
        self.assertTrue(channel.is_file(), "Staff channel tick is missing")
        self.assertTrue(interrupt.is_file(), "Staff channel interrupt is missing")
        text = channel.read_text()
        for tick in ["20", "40", "60", "100"]:
            self.assertIn(tick, text)
        self.assertIn("md_lock", interrupt.read_text())

    def test_target_raycast_assigns_stable_target_ids(self):
        start = FN / "staff/target/raycast_start.mcfunction"
        ray = FN / "staff/target/raycast.mcfunction"
        register = FN / "staff/target/register_id.mcfunction"
        for path in (start, ray, register):
            self.assertTrue(path.is_file(), f"missing Staff target function: {path.name}")
        self.assertIn("$next_tid", register.read_text())
        self.assertIn("md_tid", register.read_text())
        self.assertIn("md_lock", start.read_text())
        self.assertIn("..31", ray.read_text())

    def test_full_petrification_has_normal_and_boss_release_limits(self):
        full = FN / "staff/channel/full_petrify.mcfunction"
        release_tick = FN / "staff/target/tick_petrification.mcfunction"
        self.assertTrue(full.is_file(), "Staff full-petrify function is missing")
        self.assertTrue(release_tick.is_file(), "Staff petrification release tick is missing")
        text = full.read_text()
        self.assertIn("100", text)
        self.assertIn("30", text)
        self.assertIn("md.boss", text)
        self.assertIn("release_target", release_tick.read_text())

    def test_channel_interrupts_when_caster_was_hurt(self):
        channel = FN / "staff/channel/tick.mcfunction"
        self.assertTrue(channel.is_file(), "Staff channel tick is missing")
        self.assertIn("md.staff_interrupted", channel.read_text())

class StaffBossResistanceContract(unittest.TestCase):
    def test_medusa_boss_director_pauses_while_staff_petrified(self):
        boss_tick = FN / "boss/tick_one.mcfunction"
        self.assertTrue(boss_tick.is_file())
        self.assertIn("unless entity @s[tag=md.staff_petrified]", boss_tick.read_text())

class StoneSpikesContract(unittest.TestCase):
    def test_spikes_cost_four_and_cluster_never_edits_terrain(self):
        start = FN / "staff/spikes/start.mcfunction"
        cluster = FN / "staff/spikes/spawn_cluster.mcfunction"
        self.assertTrue(start.is_file(), "Stone Spikes start function is missing")
        self.assertTrue(cluster.is_file(), "Stone Spikes cluster function is missing")
        self.assertIn("remove @s md_staff 4", start.read_text())
        cluster_text = cluster.read_text()
        self.assertNotIn(" setblock ", cluster_text)
        self.assertNotIn(" fill ", cluster_text)

    def test_spikes_trace_ground_before_spending_charges(self):
        start = FN / "staff/spikes/start.mcfunction"
        ray = FN / "staff/spikes/ground_ray.mcfunction"
        self.assertTrue(start.is_file(), "Stone Spikes start function is missing")
        self.assertTrue(ray.is_file(), "Stone Spikes ground ray is missing")
        text = start.read_text()
        self.assertIn("md_staff_hit", text)
        self.assertIn("matches 1", text)
        self.assertIn("..23", ray.read_text())

class StoneSpikesChargeSafetyContract(unittest.TestCase):
    def test_spike_cluster_requires_successful_four_charge_payment(self):
        start = FN / "staff/spikes/start.mcfunction"
        self.assertTrue(start.is_file())
        text = start.read_text()
        self.assertIn("$spikes_paid", text)
        self.assertIn("if score $spikes_paid md_tmp matches 1", text)
        self.assertIn("run function medusa:staff/spikes/spawn_cluster", text)
