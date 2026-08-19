import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "resourcepacks/medusa"
FN = ROOT / "datapacks/medusa/data/medusa/function"


class VisualContract(unittest.TestCase):
    def test_resource_pack_format_and_approved_skin(self):
        meta_path = RP / "pack.mcmeta"
        skin = RP / "assets/medusa/textures/entity/medusa_base.png"
        self.assertTrue(meta_path.is_file(), "Medusa resource-pack metadata is missing")
        self.assertTrue(skin.is_file(), "approved Medusa base skin is missing")
        meta = json.loads(meta_path.read_text())
        self.assertEqual(meta["pack"]["min_format"], [84, 0])
        self.assertEqual(meta["pack"]["max_format"], [84, 0])
        self.assertEqual(
            hashlib.sha256(skin.read_bytes()).hexdigest(),
            "61264abec5fd01adfd30d83e014a21e476ead278df283ef014c966aea9d51c11",
        )

    def test_emf_model_targets_only_named_medusa_husk(self):
        props = RP / "assets/minecraft/emf/cem/husk/husk.properties"
        model = RP / "assets/minecraft/emf/cem/husk/husk2.jem"
        self.assertTrue(props.is_file(), "EMF Medusa selector is missing")
        self.assertTrue(model.is_file(), "EMF Medusa model is missing")
        text = props.read_text()
        self.assertIn("name.1=Medusa", text)
        model_text = model.read_text()
        for token in ["serpent", "snake", "claw", "stone"]:
            self.assertIn(token, model_text.lower())

    def test_medusa_emf_base_parts_use_husk_coordinate_system(self):
        model = json.loads((RP / "assets/minecraft/emf/cem/husk/husk2.jem").read_text())
        expected = {
            "head": [0, -24, 0],
            "body": [0, -24, 0],
            "left_arm": [5, -22, 0],
            "right_arm": [-5, -22, 0],
        }
        base_parts = {}
        for part in model["models"]:
            name = part.get("part")
            if name in expected and part.get("attach") is False and name not in base_parts:
                base_parts[name] = part

        self.assertEqual(set(base_parts), set(expected), "Medusa must replace each Husk humanoid base part exactly once")
        for name, translation in expected.items():
            self.assertEqual(base_parts[name].get("invertAxis"), "xy", f"{name} must use the Husk CEM axis system")
            self.assertEqual(base_parts[name].get("translate"), translation, f"{name} has the wrong Husk CEM pivot")

    def test_custom_item_model_mappings_exist(self):
        for item in ["medusa_staff", "medusa_heart", "gorgon_scale", "serpent_fang"]:
            path = RP / f"assets/medusa/items/{item}.json"
            self.assertTrue(path.is_file(), f"missing custom item model mapping: {item}")
            data = json.loads(path.read_text())
            self.assertEqual(data["model"]["type"], "minecraft:model")

    def test_custom_item_models_do_not_reference_removed_scute_model(self):
        offenders = []
        for path in (RP / "assets/medusa/items").glob("*.json"):
            data = json.loads(path.read_text())
            if data.get("model", {}).get("model") == "minecraft:item/scute":
                offenders.append(path.name)
        self.assertEqual(offenders, [], "26.1.2 has no minecraft:item/scute model; offenders: " + ", ".join(offenders))

    def test_boss_name_is_not_serialized_json_text(self):
        bootstrap = (FN / "boss/bootstrap.mcfunction").read_text()
        self.assertNotIn(
            "CustomName:'{\\\"text\\\":\\\"Medusa\\\"",
            bootstrap,
            "26.1.2 renders the serialized JSON string literally above the boss",
        )
