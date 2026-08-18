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

    def test_petrification_is_in_resistance_bypass_tag(self):
        tag_path = DP / "data/minecraft/tags/damage_type/bypasses_resistance.json"
        self.assertTrue(tag_path.is_file(), "missing minecraft:bypasses_resistance damage-type tag")
        tag = json.loads(tag_path.read_text())
        self.assertIn("medusa:petrification", tag.get("values", []))
