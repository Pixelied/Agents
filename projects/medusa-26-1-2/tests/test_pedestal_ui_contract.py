from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function/arena/pedestal"


class PedestalUiContract(unittest.TestCase):
    def test_locked_eye_interaction_points_to_the_shifting_labyrinth(self):
        resolve = (FN / "resolve.mcfunction").read_text()
        self.assertIn("medusa:arena/pedestal/locked_feedback", resolve)
        feedback = FN / "locked_feedback.mcfunction"
        self.assertTrue(feedback.is_file(), "locked Eye feedback function is missing")
        text = feedback.read_text().lower()
        self.assertIn("tellraw", text)
        self.assertIn("labyrinth", text)
        self.assertIn("sanctum", text)
        self.assertNotIn("three trials", text)


if __name__ == "__main__":
    unittest.main()
