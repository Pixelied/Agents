import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class RepositoryContractTests(unittest.TestCase):
    def test_agent_entrypoint_contains_complete_startup_sequence(self):
        text = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        for required in (
            "Read before doing anything",
            "python agentctl.py register",
            "python agentctl.py status",
            "python agentctl.py claim",
            "python agentctl.py heartbeat",
            "python agentctl.py handoff",
            "python agentctl.py release",
            "python agentctl.py validate",
        ):
            self.assertIn(required, text)

    def test_machine_manifest_points_agents_to_entrypoint_and_cli(self):
        manifest = json.loads((ROOT / ".agent-workspace.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["entrypoint"], "AGENTS.md")
        self.assertEqual(manifest["cli"], "python agentctl.py")
        self.assertEqual(manifest["protocol_version"], "1.0")
        self.assertEqual(manifest["commands"]["claim_scope"], "python agentctl.py claim")
        self.assertEqual(manifest["commands"]["handoff"], "python agentctl.py handoff")

    def test_provider_files_forward_to_universal_instructions(self):
        for path in (
            ROOT / "CLAUDE.md",
            ROOT / "GEMINI.md",
            ROOT / ".github" / "copilot-instructions.md",
        ):
            self.assertIn("AGENTS.md", path.read_text(encoding="utf-8"))

    def test_json_templates_are_valid(self):
        for path in (ROOT / "templates").glob("*.json"):
            json.loads(path.read_text(encoding="utf-8"))

    def test_workflow_runs_tests_and_validator(self):
        text = (ROOT / ".github" / "workflows" / "validate.yml").read_text(encoding="utf-8")
        self.assertIn("python -m unittest discover -s tests -v", text)
        self.assertIn("python agentctl.py validate", text)


if __name__ == "__main__":
    unittest.main()
