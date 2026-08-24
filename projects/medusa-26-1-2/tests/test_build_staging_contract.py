from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class BuildStagingContract(unittest.TestCase):
    def test_registration_starts_staged_build_instead_of_monolithic_dispatch(self):
        load = (FN / "load.mcfunction").read_text()
        register = (FN / "instance/register.mcfunction").read_text()
        tick = (FN / "instance/tick_one.mcfunction").read_text()
        self.assertIn("scoreboard objectives add md_build dummy", load)
        self.assertIn("scoreboard players set @s md_build 1", register)
        self.assertNotIn("function medusa:dungeon/build_generated", register)
        self.assertNotIn("function medusa:maze/setup/start", register)
        self.assertIn("function medusa:instance/build/tick", tick)
        self.assertIn("md_build matches 0", tick)

    def test_builder_executes_at_most_one_heavy_architecture_stage_per_tick(self):
        build_tick = FN / "instance/build/tick.mcfunction"
        self.assertTrue(build_tick.is_file(), "staged temple build dispatcher is missing")
        text = build_tick.read_text()
        self.assertIn("scoreboard players operation @s md_tmp = @s md_build", text)
        for stage in range(1, 13):
            self.assertIn(f"md_tmp matches {stage}", text)
            self.assertIn(f"function medusa:instance/build/stage_{stage}", text)

        heavy_functions = [
            "medusa:dungeon/build_surface",
            "medusa:dungeon/maze/build_shell",
            "medusa:dungeon/maze/build_roofs",
            "medusa:dungeon/maze/build_landmarks",
            "medusa:dungeon/maze/build_containment",
            "medusa:dungeon/build_descent",
            "medusa:dungeon/build_sanctum",
            "medusa:dungeon/build_arena",
            "medusa:dungeon/build_arena_approach",
        ]
        for stage in range(1, 10):
            stage_text = (FN / f"instance/build/stage_{stage}.mcfunction").read_text()
            present = sum(function in stage_text for function in heavy_functions)
            self.assertEqual(present, 1, f"build stage {stage} must run exactly one heavy architecture module")

    def test_final_build_stages_start_runtime_only_after_architecture(self):
        stage10 = (FN / "instance/build/stage_10.mcfunction").read_text()
        stage11 = (FN / "instance/build/stage_11.mcfunction").read_text()
        stage12 = (FN / "instance/build/stage_12.mcfunction").read_text()
        self.assertIn("function medusa:maze/setup/start", stage10)
        self.assertIn("function medusa:arena/pedestal/spawn_eye", stage11)
        self.assertIn("function medusa:instance/participants/register_initial", stage12)
        self.assertIn("scoreboard players set @s md_build 0", stage12)


if __name__ == "__main__":
    unittest.main()
