from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function/maze/generate"


class MazeGeneratorPositionContract(unittest.TestCase):
    def test_direction_open_functions_run_from_source_cell(self):
        cases = {
            "try_direction.mcfunction": [
                "open_north", "open_east", "open_south", "open_west",
            ],
            "loop_from_cell.mcfunction": [
                "loop_open_north", "loop_open_east", "loop_open_south", "loop_open_west",
            ],
        }
        for filename, functions in cases.items():
            text = (FN / filename).read_text()
            for function in functions:
                self.assertIn(
                    f" at @s run function medusa:maze/generate/{function}",
                    text,
                    f"{filename} must reset execution to the source cell before {function}; "
                    "otherwise the callee's own +/-7 block offset targets a cell two steps away",
                )


if __name__ == "__main__":
    unittest.main()
