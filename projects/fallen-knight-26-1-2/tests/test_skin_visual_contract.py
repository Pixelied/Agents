import hashlib
import json
import pathlib
import struct
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/fallen_knight/data/fallen_knight/function"
RP = ROOT / "resourcepacks/fallen_knight"
TEXTURE = RP / "assets/fallen_knight/textures/entity/fallen_knight.png"
CEM = RP / "assets/minecraft/emf/cem/vindicator"
PROPS = CEM / "vindicator.properties"
MODEL = CEM / "vindicator2.jem"
EXPECTED_SKIN_SHA256 = "df580fd6d61af70d35396ad84fc9f2e675f188da7ac4321d984fb6e90fb62a1d"


class SkinVisualContractTests(unittest.TestCase):
    def text(self, rel):
        return (DP / rel).read_text(encoding="utf-8")

    def test_skin_one_is_bundled_unchanged_as_64x64_png(self):
        raw = TEXTURE.read_bytes()
        self.assertTrue(raw.startswith(b"\x89PNG\r\n\x1a\n"))
        width, height = struct.unpack(">II", raw[16:24])
        self.assertEqual((width, height), (64, 64))
        self.assertEqual(hashlib.sha256(raw).hexdigest(), EXPECTED_SKIN_SHA256)

    def test_only_named_fallen_knight_uses_custom_vindicator_model(self):
        props = PROPS.read_text(encoding="utf-8")
        self.assertIn("models.1=2", props)
        self.assertIn("name.1=The Fallen Knight", props)

    def test_custom_model_uses_skin_one_and_player_uv_layout(self):
        model = json.loads(MODEL.read_text(encoding="utf-8"))
        self.assertEqual(model["texture"], "fallen_knight:textures/entity/fallen_knight")
        self.assertEqual(model["textureSize"], [64, 64])
        parts = {entry["part"] for entry in model["models"]}
        self.assertTrue({"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}.issubset(parts))
        # Illager-only geometry must be replaced/neutralized so the player skin does not
        # render with a vanilla nose/robe/crossed-arm model on top of it.
        self.assertTrue({"nose", "hat", "arms"}.issubset(parts))

    def test_boss_has_stable_custom_name_for_emf_rule(self):
        bootstrap = self.text("boss/bootstrap.mcfunction")
        self.assertIn('CustomName:\'{"text":"The Fallen Knight"}\'', bootstrap)
        self.assertIn("CustomNameVisible:0b", bootstrap)

    def test_skin_does_not_add_a_second_hitbox_entity(self):
        # The actual Vindicator carrier is reskinned directly through EMF. A mannequin
        # overlay would intercept melee/projectiles and change boss combat semantics.
        for path in DP.rglob("*.mcfunction"):
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("minecraft:mannequin", text, path)
            self.assertNotIn("fk.visual", text, path)


if __name__ == "__main__":
    unittest.main()
