from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class MazeInstanceContract(unittest.TestCase):
    def test_maze_objectives_and_instance_scoped_cells_exist(self):
        load = (FN / "load.mcfunction").read_text()
        for obj in [
            "md_mrow", "md_mcol", "md_mn", "md_me", "md_ms", "md_mw",
            "md_nn", "md_ne", "md_ns", "md_nw", "md_mseen", "md_mfront",
            "md_mdist", "md_mparent", "md_mphase", "md_mtick", "md_mtry",
            "md_mdelta", "md_mblocked",
        ]:
            self.assertIn(f"scoreboard objectives add {obj} dummy", load)

        spawn = (FN / "maze/setup/spawn_cell.mcfunction").read_text()
        self.assertIn("md.maze.cell", spawn)
        self.assertIn("md_eid", spawn)
        self.assertIn("md_mrow", spawn)
        self.assertIn("md_mcol", spawn)

    def test_registration_starts_runtime_generation(self):
        register = (FN / "instance/register.mcfunction").read_text()
        self.assertIn("scoreboard players set @s md_mphase 0", register)
        self.assertIn("function medusa:maze/setup/start", register)
        tick = (FN / "instance/tick_one.mcfunction").read_text()
        self.assertIn("function medusa:maze/generate/tick", tick)

    def test_initial_generator_uses_runtime_random_and_mirrored_edges(self):
        choose = (FN / "maze/generate/try_direction.mcfunction").read_text()
        self.assertIn("random value 1..4", choose)
        step = (FN / "maze/generate/step.mcfunction").read_text()
        self.assertIn("md_mparent", step)
        mirrored = "\n".join(
            (FN / rel).read_text()
            for rel in [
                "maze/generate/open_north.mcfunction",
                "maze/generate/open_east.mcfunction",
                "maze/generate/open_south.mcfunction",
                "maze/generate/open_west.mcfunction",
            ]
        )
        for pair in [("md_mn", "md_ms"), ("md_me", "md_mw")]:
            self.assertIn(pair[0], mirrored)
            self.assertIn(pair[1], mirrored)


if __name__ == "__main__":
    unittest.main()
