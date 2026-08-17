import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/fallen_knight/data/fallen_knight/function"

class ArenaContractTests(unittest.TestCase):
    def text(self, rel):
        return (FN / rel).read_text(encoding="utf-8")

    def test_each_seed_allocates_a_unique_numeric_id(self):
        text = self.text("arena/register_seed.mcfunction")
        self.assertIn("scoreboard players add $next fk_aid 1", text)
        self.assertIn("scoreboard players operation @s fk_aid = $next fk_aid", text)

    def test_start_health_scaling_is_fixed(self):
        expected = {"1":160,"2":240,"3":296,"4":344,"5":368,"6":392,"7":416,"8plus":440}
        for name, hp in expected.items():
            text = self.text(f"arena/scale/{name}.mcfunction")
            self.assertIn(f"fk_maxhp {hp}", text)

    def test_each_attempt_allocates_a_fresh_encounter_id(self):
        text = self.text("arena/start.mcfunction")
        self.assertIn("scoreboard players add $nextenc fk_eid 1", text)
        self.assertIn("scoreboard players operation @s fk_eid = $nextenc fk_eid", text)

    def test_cleanup_removes_per_arena_transients(self):
        text = self.text("arena/cleanup.mcfunction")
        self.assertIn("fk.spectral", self.text("arena/cleanup_for_arena.mcfunction"))
        self.assertIn("fk.wave", self.text("arena/cleanup_for_arena.mcfunction"))
        self.assertIn("arena/bossbar/remove", text)
        self.assertIn("arena/unseal", text)
        self.assertNotIn("participants/clear_for_arena", text)

    def test_macro_lines_always_reference_a_variable(self):
        for path in FN.rglob("*.mcfunction"):
            for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if line.startswith("$"):
                    self.assertIn("$(", line, f"{path.relative_to(FN)}:{lineno} has a macro prefix but no variable")

    def test_barrier_channel_is_symmetric(self):
        seal = self.text("arena/seal.mcfunction")
        for token in ("~-13", "~13", "minecraft:barrier"):
            self.assertIn(token, seal)

if __name__ == "__main__":
    unittest.main()
