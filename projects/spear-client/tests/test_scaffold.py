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
            "gradle/wrapper/gradle-wrapper.properties",
            "gradlew",
            "gradlew.bat",
            ".gitignore",
            "src/main/resources/fabric.mod.json",
            "src/client/resources/spearclient.mixins.json",
            "src/client/resources/assets/spearclient/lang/en_us.json",
            "src/client/java/dev/adrien/spearclient/SpearClient.java",
        ]
        missing = [path for path in required if not (ROOT / path).is_file()]
        self.assertEqual([], missing, f"missing scaffold files: {missing}")

    def test_versions_match_approved_contract(self):
        props = {}
        for raw in (ROOT / "gradle.properties").read_text().splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()

        self.assertEqual("26.1.2", props.get("minecraft_version"))
        self.assertEqual("0.19.3", props.get("loader_version"))
        self.assertEqual("1.17-SNAPSHOT", props.get("loom_version"))
        self.assertEqual("0.155.2+26.1.2", props.get("fabric_api_version"))

    def test_fabric_metadata_is_client_only_and_exact(self):
        metadata = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
        self.assertEqual("spearclient", metadata.get("id"))
        self.assertEqual("client", metadata.get("environment"))
        self.assertEqual("26.1.2", metadata["depends"].get("minecraft"))
        self.assertEqual(">=25", metadata["depends"].get("java"))
        self.assertEqual(">=0.19.3", metadata["depends"].get("fabricloader"))
        self.assertEqual("0.155.2+26.1.2", metadata["depends"].get("fabric-api"))
        self.assertEqual(["dev.adrien.spearclient.SpearClient"], metadata["entrypoints"].get("client"))

    def test_build_uses_unobfuscated_26_1_conventions(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("id 'net.fabricmc.fabric-loom' version \"${loom_version}\"", build)
        self.assertNotIn("mappings ", build)
        self.assertIn("splitEnvironmentSourceSets()", build)
        self.assertIn("options.release = 25", build)
        self.assertIn('implementation "net.fabricmc:fabric-loader:${project.loader_version}"', build)
        self.assertIn('implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"', build)
        self.assertIn('testImplementation "org.junit.jupiter:junit-jupiter:5.12.2"', build)


if __name__ == "__main__":
    unittest.main()
