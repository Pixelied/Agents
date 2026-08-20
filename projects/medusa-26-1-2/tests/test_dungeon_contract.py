from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function/dungeon"
OUT = FN / "build_generated.mcfunction"


class DungeonContract(unittest.TestCase):
    def test_generated_temple_is_current_cinematic_v2(self):
        result = subprocess.run(
            ["python3", "scripts/generate_temple.py", "--check"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        self.assertTrue(OUT.is_file(), "generated temple function is missing")
        text = OUT.read_text()
        self.assertIn("# generated from CINEMATIC_MAZE_V2", text)
        self.assertNotIn("FIXED_MAZE_V1", text)
        for function in [
            "build_surface",
            "build_descent",
            "build_puzzle_averted_room",
            "build_puzzle_borrowed_room",
            "build_puzzle_blind_room",
            "build_sanctum",
            "build_arena_approach",
            "build_arena",
        ]:
            self.assertIn(f"function medusa:dungeon/{function}", text)
        self.assertGreater(text.count("fill "), 80, "cinematic labyrinth is too sparse/simple")

    def test_route_modules_declare_every_physical_connector(self):
        connectors = {
            "build_surface.mcfunction": "surface->descent",
            "build_descent.mcfunction": "descent->averted",
            "build_puzzle_averted_room.mcfunction": "averted->labyrinth",
            "build_puzzle_borrowed_room.mcfunction": "borrowed->blind",
            "build_puzzle_blind_room.mcfunction": "blind->sanctum",
            "build_sanctum.mcfunction": "sanctum->arena_approach",
            "build_arena_approach.mcfunction": "arena_approach->arena",
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


class PuzzleContract(unittest.TestCase):
    def test_required_puzzles_exist(self):
        fn = ROOT / "datapacks/medusa/data/medusa/function/puzzle"
        for rel in [
            "averted_eyes/tick.mcfunction",
            "borrowed_gaze/tick.mcfunction",
            "blind_passage/tick.mcfunction",
        ]:
            self.assertTrue((fn / rel).is_file(), f"missing required puzzle function: {rel}")

    def test_each_puzzle_has_a_dedicated_visible_room(self):
        rooms = {
            "build_puzzle_averted_room.mcfunction": ["Averted Eyes", "stone_button", "chiseled_stone_bricks"],
            "build_puzzle_borrowed_room.mcfunction": ["Borrowed Gaze", "stone_button", "lightning_rod"],
            "build_puzzle_blind_room.mcfunction": ["Blind Passage", "observer", "redstone_lamp"],
        }
        for name, tokens in rooms.items():
            path = FN / name
            self.assertTrue(path.is_file(), f"missing dedicated puzzle room: {name}")
            text = path.read_text()
            for token in tokens:
                self.assertIn(token, text, f"{name} does not visibly communicate {token}")
            commands = [line for line in text.splitlines() if line and not line.startswith("#")]
            self.assertGreaterEqual(len(commands), 18, f"{name} is too visually sparse to read as a puzzle chamber")

    def test_generated_temple_contains_puzzle_controls(self):
        text = OUT.read_text()
        self.assertIn("function medusa:dungeon/build_puzzle_averted_room", text)
        self.assertIn("function medusa:dungeon/build_puzzle_borrowed_room", text)
        self.assertIn("function medusa:dungeon/build_puzzle_blind_room", text)
