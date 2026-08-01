import json
import tempfile
import unittest
from pathlib import Path

from src.agent_workspace.core import Workspace, WorkspaceError


class AgentLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.ws = Workspace(self.root)
        self.ws.initialize()
        self.ws.register_agent("agent-a", "openai", "model", ["python"])

    def tearDown(self):
        self.tempdir.cleanup()

    def test_agent_state_updates_profile_with_reason_and_timestamp(self):
        profile = self.ws.set_agent_state("agent-a", "offline", "session finished")
        self.assertEqual(profile["state"], "offline")
        self.assertEqual(profile["state_reason"], "session finished")
        self.assertIn("state_updated_at", profile)

    def test_agent_cannot_go_offline_while_holding_active_lease(self):
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=30)
        with self.assertRaisesRegex(WorkspaceError, "active lease"):
            self.ws.set_agent_state("agent-a", "offline", "done")

    def test_agent_list_derives_idle_and_busy_states(self):
        agent = self.ws.list_agents()[0]
        self.assertEqual(agent["declared_state"], "available")
        self.assertEqual(agent["effective_state"], "idle")
        self.assertEqual(agent["active_lease_count"], 0)

        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=30)
        agent = self.ws.list_agents()[0]
        self.assertEqual(agent["effective_state"], "busy")
        self.assertEqual(agent["active_lease_count"], 1)

    def test_offline_agent_cannot_claim_new_work(self):
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.ws.set_agent_state("agent-a", "offline", "session paused")
        with self.assertRaisesRegex(WorkspaceError, "cannot claim new work"):
            self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=30)

    def test_retired_agent_cannot_be_reactivated(self):
        self.ws.set_agent_state("agent-a", "retired", "one-shot agent complete")
        with self.assertRaisesRegex(WorkspaceError, "cannot be reactivated"):
            self.ws.set_agent_state("agent-a", "available", "returning")

    def test_validate_rejects_unknown_agent_state(self):
        profile_path = self.root / "agents" / "agent-a" / "profile.json"
        profile = json.loads(profile_path.read_text(encoding="utf-8"))
        profile["state"] = "zombie"
        profile_path.write_text(json.dumps(profile), encoding="utf-8")
        self.assertTrue(any("invalid state" in error for error in self.ws.validate()))


if __name__ == "__main__":
    unittest.main()
