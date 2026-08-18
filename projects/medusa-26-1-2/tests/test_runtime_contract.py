from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"

class RuntimeEntrypointContract(unittest.TestCase):
    def test_debug_smoke_entrypoints_exist(self):
        for rel in [
            "debug/create_test_temple.mcfunction",
            "debug/start_test_boss.mcfunction",
            "debug/give_test_items.mcfunction",
            "debug/test_petrification_damage.mcfunction",
        ]:
            self.assertTrue((FN / rel).is_file(), f"missing runtime debug entrypoint: {rel}")
