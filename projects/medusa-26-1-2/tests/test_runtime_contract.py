import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
DP = ROOT / "datapacks/medusa"
FN = DP / "data/medusa/function"


class RuntimeEntrypointContract(unittest.TestCase):
    def test_debug_smoke_entrypoints_exist(self):
        for rel in [
            "debug/create_test_temple.mcfunction",
            "debug/start_test_boss.mcfunction",
            "debug/give_test_items.mcfunction",
            "debug/test_petrification_damage.mcfunction",
            "debug/test_gaze_pipeline.mcfunction",
        ]:
            self.assertTrue((FN / rel).is_file(), f"missing runtime debug entrypoint: {rel}")

    def test_debug_harness_waits_for_entity_ready_chunk(self):
        entry = (FN / "debug/create_test_temple.mcfunction").read_text()
        waiter = FN / "debug/wait_for_test_chunk.mcfunction"
        self.assertIn("forceload add 0 0 96 96", entry)
        self.assertIn("schedule function medusa:debug/wait_for_test_chunk 1t replace", entry)
        self.assertNotIn("create_test_temple_loaded 5t", entry)
        self.assertTrue(waiter.is_file(), "entity-readiness wait function is missing")
        text = waiter.read_text()
        self.assertIn("md.chunk_probe", text)
        self.assertIn("schedule function medusa:debug/wait_for_test_chunk 1t replace", text)
        self.assertIn("schedule function medusa:debug/create_test_temple_loaded 1t replace", text)

    def test_scheduled_loaded_harness_runs_smoke_after_temple_build(self):
        text = (FN / "debug/create_test_temple_loaded.mcfunction").read_text()
        temple_pos = text.find("function medusa:admin/place_temple")
        dungeon_pos = text.find("function medusa:debug/test_dungeon_progression")
        boss_pos = text.find("function medusa:debug/start_test_boss")
        damage_pos = text.find("function medusa:debug/test_petrification_damage")
        gaze_pos = text.find("function medusa:debug/test_gaze_pipeline")
        done_pos = text.find("MEDUSA_SMOKE_DONE")
        self.assertGreaterEqual(temple_pos, 0)
        self.assertGreater(dungeon_pos, temple_pos, "dungeon smoke must run after the blocking temple build returns")
        self.assertGreater(boss_pos, dungeon_pos, "boss smoke must run after dungeon progression")
        self.assertGreater(damage_pos, boss_pos, "damage smoke must run after boss bootstrap")
        self.assertGreater(gaze_pos, damage_pos, "gaze smoke must run after the damage probe")
        self.assertGreater(done_pos, gaze_pos, "smoke completion marker must be emitted last")

    def test_exact_runtime_workflow_waits_for_in_game_smoke_completion(self):
        workflow = (REPO / ".github/workflows/medusa-26-1-2-ci.yml").read_text()
        self.assertIn("grep -q 'MEDUSA_SMOKE_DONE' server.log", workflow)
        self.assertIn("grep -q 'MEDUSA_GAZE_ANGLE_OK' server.log", workflow)
        self.assertIn("grep -q 'MEDUSA_GAZE_LOS_OK' server.log", workflow)
        self.assertIn("grep -q 'MEDUSA_GAZE_PETRIFICATION_OK' server.log", workflow)
        self.assertNotIn("echo 'function medusa:debug/start_test_boss'", workflow)
        self.assertNotIn("echo 'function medusa:debug/test_petrification_damage'", workflow)
        self.assertIn("Serialization errors", workflow, "runtime gate must reject entity/data serialization warnings")

    def test_all_display_transformations_use_complete_26_1_2_map(self):
        required = ["translation:", "left_rotation:", "scale:", "right_rotation:"]
        bad = []
        for path in FN.rglob("*.mcfunction"):
            for lineno, line in enumerate(path.read_text().splitlines(), 1):
                if "transformation:{" not in line:
                    continue
                transform = line.split("transformation:{", 1)[1]
                missing = [key for key in required if key not in transform]
                if missing:
                    bad.append(f"{path.relative_to(ROOT)}:{lineno} missing {','.join(missing)}")
        self.assertEqual(
            bad,
            [],
            "26.1.2 display transformation maps must provide translation/left_rotation/scale/right_rotation:\n"
            + "\n".join(bad),
        )

    def test_mcfunction_macro_lines_have_variables(self):
        bad = []
        for path in FN.rglob("*.mcfunction"):
            for lineno, line in enumerate(path.read_text().splitlines(), 1):
                stripped = line.lstrip()
                if stripped.startswith("$") and "$(" not in stripped:
                    bad.append(f"{path.relative_to(ROOT)}:{lineno}: {line}")
        self.assertEqual(bad, [], "macro-prefixed lines without variables:\n" + "\n".join(bad))

    def test_removed_scute_id_is_not_used(self):
        offenders = []
        for path in DP.rglob("*"):
            if path.is_file() and path.suffix in {".json", ".mcfunction"}:
                if "minecraft:scute" in path.read_text():
                    offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual(offenders, [], "26.1.2 removed minecraft:scute; offenders: " + ", ".join(offenders))

    def test_shaped_recipe_uses_26_1_2_ingredient_codec(self):
        recipe = json.loads((DP / "data/medusa/recipe/medusa_staff.json").read_text())
        for symbol, ingredient in recipe["key"].items():
            self.assertTrue(
                isinstance(ingredient, (str, list)),
                f"26.1.2 shaped ingredient {symbol} must be an item/tag string or list, got {ingredient!r}",
            )

    def test_petrification_suffocation_opens_a_resistance_damage_window(self):
        text = (FN / "petrify/suffocate.mcfunction").read_text()
        clear_pos = text.find("effect clear @s minecraft:resistance")
        damage_pos = text.find("damage @s 2 medusa:petrification")
        restore_pos = text.find("effect give @s minecraft:resistance")
        self.assertGreaterEqual(clear_pos, 0, "suffocation must temporarily clear Resistance")
        self.assertGreater(damage_pos, clear_pos, "damage must happen after Resistance is cleared")
        self.assertGreater(restore_pos, damage_pos, "Resistance must be restored immediately after damage")

        debug = (FN / "debug/test_petrification_damage.mcfunction").read_text()
        self.assertIn("effect clear @e[type=minecraft:husk,tag=md.damage_probe,limit=1] minecraft:resistance", debug)
        self.assertIn("effect give @e[type=minecraft:husk,tag=md.damage_probe,limit=1] minecraft:resistance", debug)

    def test_manual_damage_probe_spawns_in_the_executors_loaded_chunk(self):
        debug = (FN / "debug/test_petrification_damage.mcfunction").read_text()
        self.assertNotIn("summon minecraft:husk 8 101 0", debug, "manual debug probe must not depend on the CI world's fixed origin")
        self.assertIn("summon minecraft:husk ~ ~ ~", debug, "manual debug probe should spawn beside the command executor")

    def test_removed_husk_attack_sound_is_not_used(self):
        offenders = []
        for path in FN.rglob("*.mcfunction"):
            if "minecraft:entity.husk.attack" in path.read_text():
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual(offenders, [], "26.1.2 reports minecraft:entity.husk.attack as unknown: " + ", ".join(offenders))


if __name__ == "__main__":
    unittest.main()
