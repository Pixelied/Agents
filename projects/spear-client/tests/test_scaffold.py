import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class ScaffoldContractTest(unittest.TestCase):
    def test_required_project_files_exist(self):
        required = [
            "settings.gradle",
            "build.gradle",
            "gradle.properties",
            "src/main/resources/fabric.mod.json",
            "src/main/java/dev/adrien/spear/SpearClient.java",
        ]
        missing = [path for path in required if not (ROOT / path).is_file()]
        self.assertEqual([], missing, f"missing scaffold files: {missing}")

    def test_versions_and_java_match_approved_contract(self):
        props = {}
        for raw in (ROOT / "gradle.properties").read_text().splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()

        self.assertEqual("26.1.2", props.get("minecraft_version"))
        self.assertEqual("0.18.4", props.get("loader_version"))
        self.assertEqual("0.143.15+26.1", props.get("fabric_version"))
        self.assertEqual("1.15.5", props.get("loom_version"))
        self.assertEqual("25", props.get("java_version"))

    def test_fabric_metadata_is_client_only(self):
        metadata = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
        self.assertEqual("client", metadata.get("environment"))
        self.assertEqual(">=26.1.2", metadata["depends"].get("minecraft"))
        self.assertEqual(">=25", metadata["depends"].get("java"))
        self.assertIn("client", metadata.get("entrypoints", {}))

    def test_build_uses_unobfuscated_26_1_conventions(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn('id "net.fabricmc.fabric-loom"', build)
        self.assertNotIn("mappings ", build)
        self.assertIn("JavaLanguageVersion.of(25)", build)
        self.assertIn("implementation \"net.fabricmc:fabric-loader:${loader_version}\"", build)
        self.assertIn("implementation \"net.fabricmc.fabric-api:fabric-api:${fabric_version}\"", build)


if __name__ == "__main__":
    unittest.main()
