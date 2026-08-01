import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from src.agent_workspace.core import Workspace, WorkspaceError


class WorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.ws = Workspace(self.root)
        self.ws.initialize()

    def tearDown(self):
        self.tempdir.cleanup()

    def test_register_agent_creates_machine_readable_profile(self):
        profile = self.ws.register_agent(
            agent_id="codex-alpha-7f3a",
            provider="openai",
            model="gpt-5.6-thinking",
            capabilities=["python", "review"],
        )
        path = self.root / "agents" / "codex-alpha-7f3a" / "profile.json"
        self.assertTrue(path.exists())
        self.assertEqual(profile["agent_id"], "codex-alpha-7f3a")
        self.assertEqual(profile["protocol_version"], "1.0")

    def test_register_agent_refuses_duplicate_id(self):
        self.ws.register_agent("agent-a", "openai", "model-a", [])
        with self.assertRaisesRegex(WorkspaceError, "already exists"):
            self.ws.register_agent("agent-a", "openai", "model-b", [])

    def test_task_creator_must_be_registered(self):
        with self.assertRaisesRegex(WorkspaceError, "Missing required file"):
            self.ws.create_task("task-1", "Task", "missing-agent", "Do work", ["src"])

    def test_task_scopes_must_not_overlap(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        with self.assertRaisesRegex(WorkspaceError, "overlap"):
            self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src", "src/parser"])

    def test_scope_filenames_do_not_collide(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["a/b", "a__b"])
        first = self.ws.claim_scope("task-1", "a/b", "agent-a")
        self.ws.release_scope("task-1", "a/b", "agent-a")
        second = self.ws.claim_scope("task-1", "a__b", "agent-a")
        lease_files = list((self.root / "tasks" / "task-1" / "leases").glob("*.json"))
        self.assertEqual(first["scope"], "a/b")
        self.assertEqual(second["scope"], "a__b")
        self.assertEqual(len(lease_files), 2)

    def test_status_derives_latest_task_state(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.ws.set_task_state("task-1", "agent-a", "completed", "Acceptance criteria met")
        self.assertEqual(self.ws.status("task-1")["effective_status"], "completed")

    def test_lists_agents_and_tasks(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.assertEqual(self.ws.list_agents()[0]["agent_id"], "agent-a")
        self.assertEqual(self.ws.list_tasks()[0]["task_id"], "task-1")

    def test_create_task_builds_isolated_task_workspace(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        task = self.ws.create_task(
            task_id="improve-readme",
            title="Improve README",
            created_by="agent-a",
            objective="Make onboarding clearer.",
            scopes=["docs", "tests"],
        )
        self.assertEqual(task["status"], "open")
        self.assertTrue((self.root / "tasks" / "improve-readme" / "events").is_dir())
        self.assertTrue((self.root / "tasks" / "improve-readme" / "leases").is_dir())
        self.assertTrue((self.root / "tasks" / "improve-readme" / "handoffs").is_dir())

    def test_active_exclusive_lease_blocks_other_agent(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.register_agent("agent-b", "anthropic", "b", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=30)
        with self.assertRaisesRegex(WorkspaceError, "actively leased"):
            self.ws.claim_scope("task-1", "src", "agent-b", ttl_minutes=30)

    def test_expired_lease_can_be_reclaimed(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.register_agent("agent-b", "anthropic", "b", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        lease = self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=30)
        lease_path = self.root / "tasks" / "task-1" / "leases" / "src.json"
        lease["expires_at"] = (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()
        lease_path.write_text(json.dumps(lease, indent=2) + "\n", encoding="utf-8")
        reclaimed = self.ws.claim_scope("task-1", "src", "agent-b", ttl_minutes=30)
        self.assertEqual(reclaimed["owner"], "agent-b")
        self.assertEqual(reclaimed["generation"], 2)

    def test_heartbeat_extends_owned_lease(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        old = self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=5)
        new = self.ws.heartbeat("task-1", "src", "agent-a", ttl_minutes=30)
        self.assertGreater(new["expires_at"], old["expires_at"])

    def test_release_prevents_old_owner_from_appearing_active(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        self.ws.claim_scope("task-1", "src", "agent-a", ttl_minutes=30)
        released = self.ws.release_scope("task-1", "src", "agent-a", "done")
        self.assertEqual(released["state"], "released")
        self.assertFalse(self.ws.active_leases("task-1"))

    def test_events_are_append_only_and_uniquely_named(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        before = len(list((self.root / "tasks" / "task-1" / "events").glob("*.json")))
        first = self.ws.add_event("task-1", "agent-a", "progress", "Started")
        second = self.ws.add_event("task-1", "agent-a", "progress", "Continued")
        self.assertNotEqual(first[0], second[0])
        after = len(list((self.root / "tasks" / "task-1" / "events").glob("*.json")))
        self.assertEqual(after - before, 2)

    def test_handoff_contains_required_next_action(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.register_agent("agent-b", "anthropic", "b", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        path, handoff = self.ws.create_handoff(
            task_id="task-1",
            from_agent="agent-a",
            to_agent="agent-b",
            summary="Parser complete.",
            completed=["Parser"],
            remaining=["CLI"],
            next_action="Implement the CLI command.",
            files_changed=["src/parser.py"],
            verification=["python -m unittest"],
            risks=["None known"],
        )
        self.assertTrue(path.exists())
        self.assertEqual(handoff["next_action"], "Implement the CLI command.")

    def test_validate_reports_unknown_lease_owner(self):
        self.ws.register_agent("agent-a", "openai", "a", [])
        self.ws.create_task("task-1", "Task", "agent-a", "Do work", ["src"])
        lease_dir = self.root / "tasks" / "task-1" / "leases"
        lease_dir.mkdir(exist_ok=True)
        lease_dir.joinpath("src.json").write_text(
            json.dumps({
                "schema_version": 1,
                "task_id": "task-1",
                "scope": "src",
                "owner": "missing-agent",
                "state": "active",
                "claimed_at": datetime.now(timezone.utc).isoformat(),
                "heartbeat_at": datetime.now(timezone.utc).isoformat(),
                "expires_at": (datetime.now(timezone.utc) + timedelta(minutes=30)).isoformat(),
                "generation": 1,
            }),
            encoding="utf-8",
        )
        errors = self.ws.validate()
        self.assertTrue(any("unknown agent" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
