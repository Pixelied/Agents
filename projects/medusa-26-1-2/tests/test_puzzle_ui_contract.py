from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PUZZLE = ROOT / "datapacks/medusa/data/medusa/function/puzzle"


class PuzzleUiContract(unittest.TestCase):
    def test_each_puzzle_explains_its_mechanic_in_world(self):
        expected = {
            "averted_eyes/tick.mcfunction": ["title @s actionbar", "sentinel", "Gorgon"],
            "borrowed_gaze/tick.mcfunction": ["title @s actionbar", "bronze", "idol"],
            "blind_passage/tick.mcfunction": ["title @s actionbar", "watcher", "dark"],
        }
        for rel, tokens in expected.items():
            text = (PUZZLE / rel).read_text()
            for token in tokens:
                self.assertIn(token, text, f"{rel} needs an in-world clue containing {token!r}")


if __name__ == "__main__":
    unittest.main()
