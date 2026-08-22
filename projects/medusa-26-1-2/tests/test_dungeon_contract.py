from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function/dungeon"
OUT = FN / "build_generated.mcfunction"


class DungeonContract(unittest.TestCase):
    def test_generated_temple_is_current_shifting_labyrinth(self):
        result = subprocess.run(
            ["python3", "scripts/generate_temple.py", "--check"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        self.assertTrue(OUT.is_file(), "generated temple function is missing")
        text = OUT.read_text()
        self.assertIn("# generated from SHIFTING_LABYRINTH_V3", text)
        self.assertNotIn("CINEMATIC_MAZE_V2", text)
        for function in [
            "build_surface",
            "maze/build_shell",
            "maze/build_roofs",
            "maze/build_landmarks",
            "maze/build_containment",
            "build_descent",
            "build_sanctum",
            "build_arena",
            "build_arena_approach",
        ]:
            self.assertIn(f"function medusa:dungeon/{function}", text)
        self.assertNotIn("build_puzzle_averted_room", text)
        self.assertNotIn("build_puzzle_borrowed_room", text)
        self.assertNotIn("build_puzzle_blind_room", text)

    def test_shifting_labyrinth_geometry_contract(self):
        generator = (ROOT / "scripts/generate_temple.py").read_text()
        self.assertIn("MAZE_ROWS = 13", generator)
        self.assertIn("MAZE_COLS = 13", generator)
        self.assertIn("CELL_PITCH = 7", generator)
        self.assertIn("MAZE_BASE_X = -44", generator)
        self.assertIn("MAZE_BASE_Z = 30", generator)

        shell = FN / "maze/build_shell.mcfunction"
        roofs = FN / "maze/build_roofs.mcfunction"
        landmarks = FN / "maze/build_landmarks.mcfunction"
        containment = FN / "maze/build_containment.mcfunction"
        for path in [shell, roofs, landmarks, containment]:
            self.assertTrue(path.is_file(), f"missing maze architecture module: {path.name}")

        shell_text = shell.read_text()
        roof_text = roofs.read_text() + (FN / "maze/roof_column.mcfunction").read_text()
        landmark_text = landmarks.read_text()
        self.assertGreaterEqual(shell_text.count("fill "), 35, "13x13 maze shell is unexpectedly sparse")
        for token in ["stone_brick_stairs", "stone_brick_slab", "iron_chain", "soul_lantern"]:
            self.assertIn(token, roof_text, f"roof architecture is missing {token}")
        for district in [
            "serpent-column gallery",
            "moss/root crypt",
            "lava-cracked district",
            "petrified expedition",
            "tall central junction",
            "gorgon-relief corridor",
        ]:
            self.assertIn(district, landmark_text.lower())

    def test_26_1_2_uses_iron_chain_block_id(self):
        architecture = "\n".join(path.read_text() for path in FN.rglob("*.mcfunction"))
        self.assertNotIn("minecraft:chain", architecture)
        self.assertIn("minecraft:iron_chain", architecture)

    def test_route_modules_declare_every_physical_connector(self):
        connectors = {
            "build_surface.mcfunction": "surface->descent",
            "build_descent.mcfunction": "descent->maze",
            "build_sanctum.mcfunction": "maze->sanctum",
            "build_arena_approach.mcfunction": "sanctum->arena",
        }
        for name, connector in connectors.items():
            path = FN / name
            self.assertTrue(path.is_file(), f"missing route module: {name}")
            self.assertIn(f"# connector: {connector}", path.read_text())

    def test_surface_and_arena_have_authored_detail_density(self):
        for name, minimum_lines in [("build_surface.mcfunction", 55), ("build_arena.mcfunction", 55)]:
            text = (FN / name).read_text()
            commands = [line for line in text.splitlines() if line and not line.startswith("#")]
            self.assertGreaterEqual(len(commands), minimum_lines, f"{name} is still an under-detailed box")
            palette = [
                "stone_bricks",
                "cracked_stone_bricks",
                "mossy_stone_bricks",
                "chiseled_stone_bricks",
                "stone_brick_stairs",
                "stone_brick_slab",
                "cobblestone_wall",
                "soul_lantern",
            ]
            present = sum(token in text for token in palette)
            self.assertGreaterEqual(present, 6, f"{name} needs a richer authored palette")

    def test_admin_placement_delegates_to_instance_registration(self):
        path = ROOT / "datapacks/medusa/data/medusa/function/admin/place_temple.mcfunction"
        self.assertTrue(path.is_file(), "admin placement function is missing")
        self.assertIn("function medusa:instance/register", path.read_text())


class RemovedPuzzleContract(unittest.TestCase):
    def test_old_puzzle_runtime_and_rooms_are_gone(self):
        puzzle = ROOT / "datapacks/medusa/data/medusa/function/puzzle"
        self.assertFalse(puzzle.exists(), "obsolete puzzle runtime must be removed")
        for name in [
            "build_puzzle_averted_room.mcfunction",
            "build_puzzle_borrowed_room.mcfunction",
            "build_puzzle_blind_room.mcfunction",
        ]:
            self.assertFalse((FN / name).exists(), f"obsolete puzzle room still exists: {name}")


if __name__ == "__main__":
    unittest.main()
