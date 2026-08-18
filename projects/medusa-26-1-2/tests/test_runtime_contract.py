import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/medusa"
FN = DP / "data/medusa/function"


class RuntimeEntrypointContract(unittest.TestCase):
    def test_debug_smoke_entrypoints_exist(self):
        for rel in [
            "debug/create_test_temple.mcfunction",
            "debug/start_test_boss.mcfunction",
            "debug/give_test_items.mcfunction",
            "debug/test_petrification_damage.mcfunction",
        ]:
            self.assertTrue((FN / rel).is_file(), f"missing runtime debug entrypoint: {rel}")

    def test_debug_harness_loads_then_schedules_the_remote_arena(self):
        text = (FN / "debug/create_test_temple.mcfunction").read_text()
        self.assertIn("forceload add 0 0 96 96", text)
        self.assertIn("schedule function medusa:debug/create_test_temple_loaded 5t replace", text)
        loaded = FN / "debug/create_test_temple_loaded.mcfunction"
        self.assertTrue(loaded.is_file(), "scheduled loaded-chunk temple creation function is missing")
        self.assertIn("function medusa:admin/place_temple", loaded.read_text())

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
