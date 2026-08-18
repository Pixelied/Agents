import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/medusa"

class PackContract(unittest.TestCase):
    def test_exact_formats_and_entrypoints(self):
        pack_meta = DP / "pack.mcmeta"
        load_fn = DP / "data/medusa/function/load.mcfunction"
        tick_fn = DP / "data/medusa/function/tick.mcfunction"
        load_tag_path = DP / "data/minecraft/tags/function/load.json"
        tick_tag_path = DP / "data/minecraft/tags/function/tick.json"
        for path in (pack_meta, load_fn, tick_fn, load_tag_path, tick_tag_path):
            self.assertTrue(path.is_file(), f"missing required pack file: {path.relative_to(ROOT)}")
        meta = json.loads(pack_meta.read_text())
        self.assertEqual(meta["pack"]["min_format"], [101, 1])
        self.assertEqual(meta["pack"]["max_format"], [101, 1])
        self.assertEqual(json.loads(load_tag_path.read_text())["values"], ["medusa:load"])
        self.assertEqual(json.loads(tick_tag_path.read_text())["values"], ["medusa:tick"])

class ValidatorContract(unittest.TestCase):
    def test_static_validator_accepts_current_scaffold(self):
        import subprocess
        result = subprocess.run(
            ["python3", "scripts/validate_medusa.py"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("medusa static validation passed", result.stdout)

class CommandSyntaxGuard(unittest.TestCase):
    def test_score_matches_never_uses_comma_lists(self):
        import re
        function_root = DP / "data/medusa/function"
        bad = []
        pattern = re.compile(r"\bmatches\s+[^\s]+,")
        for path in function_root.rglob("*.mcfunction"):
            for line_no, line in enumerate(path.read_text().splitlines(), 1):
                if pattern.search(line):
                    bad.append(f"{path.relative_to(ROOT)}:{line_no}: {line}")
        self.assertEqual(bad, [], "scoreboard 'matches' accepts one value/range, not comma lists:\n" + "\n".join(bad))
