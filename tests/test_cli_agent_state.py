import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENTCTL = ROOT / "agentctl.py"


class CliAgentStateTests(unittest.TestCase):
    def test_agent_state_command(self):
        with tempfile.TemporaryDirectory() as temp:
            workspace = Path(temp)

            def run(*args, expect=0):
                result = subprocess.run(
                    [sys.executable, str(AGENTCTL), "--root", str(workspace), *args],
                    text=True,
                    capture_output=True,
                )
                self.assertEqual(result.returncode, expect, result.stderr or result.stdout)
                return result

            run("init")
            run("register", "--id", "agent-a", "--provider", "openai", "--model", "model")
            payload = json.loads(
                run(
                    "agent-state", "--agent", "agent-a", "--state", "offline",
                    "--reason", "session finished",
                ).stdout
            )
            self.assertEqual(payload["state"], "offline")


if __name__ == "__main__":
    unittest.main()
