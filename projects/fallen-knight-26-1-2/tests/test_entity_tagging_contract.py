import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FN = ROOT / 'datapacks/fallen_knight/data/fallen_knight/function'
GEN = ROOT / 'scripts/generate_structures.py'


class EntityTaggingContractTests(unittest.TestCase):
    def test_functions_do_not_depend_on_summon_nbt_tags(self):
        offenders = []
        for path in FN.rglob('*.mcfunction'):
            if 'Tags:[' in path.read_text(encoding='utf-8'):
                offenders.append(str(path.relative_to(FN)))
        self.assertEqual(offenders, [])

    def test_worldgen_seed_is_discovered_by_block_signature(self):
        tick = (FN / 'arena/tick_all.mcfunction').read_text(encoding='utf-8')
        self.assertIn('minecraft:lodestone', tick)
        self.assertIn('tag @s add fk.arena_seed', tick)
        self.assertNotIn('minecraft:red_terracotta', tick)
        self.assertNotIn('minecraft:blackstone', tick)
        gen = GEN.read_text(encoding='utf-8')
        self.assertIn('s.set(23,0,23,"minecraft:lodestone")', gen.replace(' ', ''))
        self.assertIn('"minecraft:marker", None', gen)

    def test_vindicator_summons_do_not_use_inline_nbt(self):
        offenders = []
        for path in FN.rglob('*.mcfunction'):
            for line in path.read_text(encoding='utf-8').splitlines():
                if 'summon minecraft:vindicator' in line and '{' in line:
                    offenders.append(f"{path.relative_to(FN)}: {line}")
        self.assertEqual(offenders, [])


if __name__ == '__main__':
    unittest.main()
