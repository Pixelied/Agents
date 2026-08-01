import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SpeedBridgeWorkflowTests(unittest.TestCase):
    def test_unconfirmed_client_startup_fails_the_job(self):
        text = (ROOT / ".github" / "workflows" / "speedbridge-assist-ci.yml").read_text(encoding="utf-8")
        self.assertIn('case "$RESULT" in', text)
        self.assertIn("timeout-before-confirmed-startup", text)
        self.assertIn("Development client startup was not confirmed", text)
        self.assertIn("exit 1", text)
        self.assertNotIn("Setting user:", text)


if __name__ == "__main__":
    unittest.main()
