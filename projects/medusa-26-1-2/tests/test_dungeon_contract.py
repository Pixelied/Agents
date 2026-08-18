from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "datapacks/medusa/data/medusa/function/dungeon/build_generated.mcfunction"

class DungeonContract(unittest.TestCase):
    def test_generated_temple_is_current(self):
        result = subprocess.run(
            ["python3", "scripts/generate_temple.py", "--check"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        self.assertTrue(OUT.is_file(), "generated temple function is missing")
        text = OUT.read_text()
        self.assertIn("# generated from FIXED_MAZE_V1", text)
        self.assertIn("function medusa:dungeon/build_surface", text)
        self.assertIn("function medusa:dungeon/build_arena", text)

    def test_admin_placement_delegates_to_instance_registration(self):
        path = ROOT / "datapacks/medusa/data/medusa/function/admin/place_temple.mcfunction"
        self.assertTrue(path.is_file(), "admin placement function is missing")
        self.assertIn("function medusa:instance/register", path.read_text())
