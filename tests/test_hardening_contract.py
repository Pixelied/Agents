import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class HardeningContractTests(unittest.TestCase):
    def test_agent_manual_documents_lifecycle_shutdown(self):
        text = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn("python agentctl.py agent-state", text)
        self.assertIn("mark one-shot agents `offline` or permanently `retired`", text)

    def test_manifest_exposes_agent_state_command(self):
        manifest = json.loads((ROOT / ".agent-workspace.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["commands"]["set_agent_state"], "python agentctl.py agent-state")

    def test_artifact_policy_rejects_base64_as_normal_storage(self):
        text = (ROOT / "docs" / "protocols" / "artifacts.md").read_text(encoding="utf-8")
        self.assertIn("Base64 is not storage compression", text)
        self.assertIn("GitHub Actions artifact", text)

    def test_agent_schema_supports_retirement_metadata(self):
        schema = json.loads((ROOT / "schemas" / "agent.schema.json").read_text(encoding="utf-8"))
        states = schema["properties"]["state"]["enum"]
        self.assertIn("retired", states)
        self.assertIn("state_updated_at", schema["properties"])


if __name__ == "__main__":
    unittest.main()
