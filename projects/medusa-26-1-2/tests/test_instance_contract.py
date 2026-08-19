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

class LifecycleContract(unittest.TestCase):
    def test_reward_guard_and_ritual_material_checks_exist(self):
        reward = FN / "reward/distribute.mcfunction"
        ritual = FN / "ritual/validate_offering.mcfunction"
        self.assertTrue(reward.is_file(), "reward distribution is missing")
        self.assertTrue(ritual.is_file(), "ritual validation is missing")
        self.assertIn("md_rewarded", reward.read_text())
        text = ritual.read_text()
        self.assertIn("gorgon_scale", text)
        self.assertIn("serpent_fang", text)

    def test_reset_and_death_entrypoints_exist(self):
        for rel in [
            "arena/reset/start.mcfunction",
            "arena/reset/finish.mcfunction",
            "boss/death/start.mcfunction",
            "boss/death/finish.mcfunction",
            "ritual/commit.mcfunction",
        ]:
            self.assertTrue((FN / rel).is_file(), f"missing lifecycle function: {rel}")

    def test_pre_spawn_ritual_interruption_refunds_pending_offering(self):
        load = (FN / "load.mcfunction").read_text()
        register = (FN / "instance/register.mcfunction").read_text()
        consume = (FN / "ritual/consume_and_commit.mcfunction").read_text()
        awakening_finish = (FN / "arena/awakening/finish.mcfunction").read_text()
        reset_finish = (FN / "arena/reset/finish.mcfunction").read_text()
        refund = FN / "ritual/refund_pending.mcfunction"
        refund_loot = ROOT / "datapacks/medusa/data/medusa/loot_table/rewards/ritual_refund.json"

        self.assertIn("scoreboard objectives add md_ritual_paid dummy", load)
        self.assertIn("scoreboard players set @s md_ritual_paid 0", register)
        self.assertIn("md_ritual_paid 1", consume)
        self.assertIn("scoreboard players set @s md_ritual_paid 0", awakening_finish)
        self.assertIn("function medusa:ritual/refund_pending", reset_finish)
        self.assertTrue(refund.is_file(), "pending ritual refund function is missing")
        self.assertIn("md_ritual_paid 0", refund.read_text())
        self.assertTrue(refund_loot.is_file(), "ritual refund loot table is missing")
        text = refund_loot.read_text()
        self.assertIn('"count": 4', text)
        self.assertIn('"md_item": "gorgon_scale"', text)
        self.assertIn('"count": 1', text)
        self.assertIn('"md_item": "serpent_fang"', text)

class InstanceIsolationContract(unittest.TestCase):
    def test_petrified_statue_helpers_are_scoped_by_instance_id(self):
        spawn = (FN / "petrify/statue/spawn.mcfunction").read_text()
        cleanup = (FN / "arena/reset/cleanup_scoped.mcfunction").read_text()

        self.assertIn(
            "scoreboard players operation @e[type=minecraft:block_display,tag=md.new_statue_shell,distance=..2,limit=1,sort=nearest] md_eid = @s md_eid",
            spawn,
            "statue shell must inherit the participant's instance id",
        )
        self.assertIn(
            "scoreboard players operation @e[type=minecraft:interaction,tag=md.new_statue_hitbox,distance=..2,limit=1,sort=nearest] md_eid = @s md_eid",
            spawn,
            "statue hitbox must inherit the participant's instance id",
        )
        self.assertIn(
            '$kill @e[type=minecraft:block_display,tag=md.statue_shell,scores={md_eid=$(eid)}]',
            cleanup,
            "instance cleanup must not delete statue shells from a neighboring temple",
        )
        self.assertIn(
            '$kill @e[type=minecraft:interaction,tag=md.statue_hitbox,scores={md_eid=$(eid)}]',
            cleanup,
            "instance cleanup must not delete statue hitboxes from a neighboring temple",
        )
        self.assertNotIn("tag=md.statue_shell,distance=..36", cleanup)
        self.assertNotIn("tag=md.statue_hitbox,distance=..36", cleanup)
