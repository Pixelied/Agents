from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class ExactParserRegressionContract(unittest.TestCase):
    def test_conditional_commands_include_run_before_summon(self):
        offenders = []
        for path in FN.rglob("*.mcfunction"):
            for lineno, line in enumerate(path.read_text().splitlines(), 1):
                stripped = line.lstrip("$")
                if stripped.startswith("execute ") and " summon minecraft:" in stripped and " run summon minecraft:" not in stripped:
                    offenders.append(f"{path.relative_to(ROOT)}:{lineno}: {line}")
        self.assertEqual(offenders, [], "execute condition missing `run` before summon:\n" + "\n".join(offenders))

    def test_abort_close_score_comparison_names_both_objectives(self):
        text = (FN / "maze/wall/abort_close.mcfunction").read_text()
        comparisons = [line for line in text.splitlines() if "if score @s md_eid = @e[" in line]
        self.assertEqual(len(comparisons), 4, "expected four instance-id score comparisons in abort_close")
        for line in comparisons:
            self.assertRegex(
                line,
                re.compile(r"if score @s md_eid = @e\[[^\]]+\] md_eid run "),
                f"score comparison is missing the target objective: {line}",
            )


if __name__ == "__main__":
    unittest.main()
