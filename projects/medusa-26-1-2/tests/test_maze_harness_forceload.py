from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class MazeHarnessForceloadContract(unittest.TestCase):
    def test_exact_runtime_forceload_covers_full_shifting_labyrinth(self):
        generator = (ROOT / "scripts/generate_temple.py").read_text()
        self.assertIn("MAZE_BASE_X = -44", generator)
        self.assertIn("MAZE_BASE_Z = 30", generator)
        entry = (FN / "debug/create_test_temple.mcfunction").read_text()
        self.assertIn("forceload add -64 0 128 128", entry)
        self.assertNotIn("forceload add 0 0 96 96", entry)


if __name__ == "__main__":
    unittest.main()
