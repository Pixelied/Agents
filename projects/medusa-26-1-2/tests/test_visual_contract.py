import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "resourcepacks/medusa"

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

    def test_custom_item_model_mappings_exist(self):
        for item in ["medusa_staff", "medusa_heart", "gorgon_scale", "serpent_fang"]:
            path = RP / f"assets/medusa/items/{item}.json"
            self.assertTrue(path.is_file(), f"missing custom item model mapping: {item}")
            data = json.loads(path.read_text())
            self.assertEqual(data["model"]["type"], "minecraft:model")
