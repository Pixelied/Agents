import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
AGENTCTL = REPO_ROOT / "agentctl.py"


class CliTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)

    def tearDown(self):
        self.tempdir.cleanup()

    def run_cli(self, *args, expect=0):
        result = subprocess.run(
            [sys.executable, str(AGENTCTL), "--root", str(self.root), *args],
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, expect, msg=result.stderr or result.stdout)
        return result

    def test_cli_complete_workflow(self):
        self.run_cli("init")
        self.run_cli(
            "register", "--id", "agent-a", "--provider", "openai",
            "--model", "model-a", "--capability", "python",
        )
        self.run_cli(
            "task-create", "--id", "task-1", "--title", "Task",
            "--created-by", "agent-a", "--objective", "Do work",
            "--scope", "src",
        )
        self.run_cli(
            "claim", "--task", "task-1", "--scope", "src",
            "--agent", "agent-a", "--ttl", "30",
        )
        agents = json.loads(self.run_cli("agent-list").stdout)
        tasks = json.loads(self.run_cli("task-list").stdout)
        self.assertEqual(agents[0]["agent_id"], "agent-a")
        self.assertEqual(tasks[0]["task_id"], "task-1")
        self.run_cli("task-state", "--task", "task-1", "--agent", "agent-a", "--state", "completed", "--message", "Done")
        status = self.run_cli("status", "--task", "task-1")
        payload = json.loads(status.stdout)
        self.assertEqual(payload["active_leases"][0]["owner"], "agent-a")
        self.assertEqual(payload["effective_status"], "completed")
        self.run_cli("validate")

    def test_cli_returns_nonzero_on_conflict(self):
        self.run_cli("init")
        for agent in ("agent-a", "agent-b"):
            self.run_cli(
                "register", "--id", agent, "--provider", "test",
                "--model", "model",
            )
        self.run_cli(
            "task-create", "--id", "task-1", "--title", "Task",
            "--created-by", "agent-a", "--objective", "Do work",
            "--scope", "src",
        )
        self.run_cli("claim", "--task", "task-1", "--scope", "src", "--agent", "agent-a")
        conflict = self.run_cli(
            "claim", "--task", "task-1", "--scope", "src", "--agent", "agent-b", expect=2
        )
        self.assertIn("actively leased", conflict.stderr)


if __name__ == "__main__":
    unittest.main()
