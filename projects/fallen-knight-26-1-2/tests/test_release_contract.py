import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / 'datapacks' / 'fallen_knight'
RP = ROOT / 'resourcepacks' / 'fallen_knight'
FN = DP / 'data' / 'fallen_knight' / 'function'


class ReleaseContractTests(unittest.TestCase):
    def test_public_debug_start_is_single_command_safe(self):
        start = FN / 'debug' / 'start_test_fight.mcfunction'
        self.assertTrue(start.exists(), 'missing public start_test_fight debug entrypoint')
        text = start.read_text(encoding='utf-8')
        self.assertFalse(any(line.startswith('$') for line in text.splitlines()))
        bootstrap = (FN / 'debug' / 'bootstrap_test_arena.mcfunction').read_text(encoding='utf-8')
        finish = (FN / 'debug' / 'start_test_fight_finish.mcfunction').read_text(encoding='utf-8')
        self.assertIn('fallen_knight:arena/register_seed', bootstrap)
        self.assertIn('fallen_knight:arena/start', finish)

    def test_legacy_create_test_arena_routes_to_working_solo_test(self):
        text = (FN / 'debug' / 'create_test_arena.mcfunction').read_text(encoding='utf-8')
        self.assertIn('function fallen_knight:debug/start_test_fight', text)

    def test_server_smoke_instantiates_macro_functions_with_arguments(self):
        smoke = FN / 'debug' / 'server_smoke.mcfunction'
        verify = FN / 'debug' / 'server_smoke_verify.mcfunction'
        self.assertTrue(smoke.exists(), 'missing console-safe runtime smoke function')
        self.assertTrue(verify.exists(), 'missing deferred runtime smoke verifier')
        spawn_text = smoke.read_text(encoding='utf-8')
        text = verify.read_text(encoding='utf-8')
        self.assertIn('execute summon minecraft:marker run function', spawn_text)
        self.assertIn('summon minecraft:vindicator run function', spawn_text)
        self.assertIn('function fallen_knight:arena/activate_boss {aid:', text)
        self.assertIn('function fallen_knight:arena/bossbar/create {aid:', text)
        self.assertIn('function fallen_knight:boss/tick_one', text)

    def test_release_builder_makes_two_directly_installable_pack_zips(self):
        builder = ROOT / 'scripts' / 'build_release.py'
        self.assertTrue(builder.exists(), 'missing release builder')
        with tempfile.TemporaryDirectory() as td:
            out = pathlib.Path(td)
            subprocess.run([sys.executable, str(builder), '--output', str(out)], cwd=ROOT, check=True)
            dp_zip = out / 'Fallen-Knight-Datapack-26.1.2.zip'
            rp_zip = out / 'Fallen-Knight-Resource-Pack-26.1.2.zip'
            bundle = out / 'Fallen-Knight-26.1.2.zip'
            for path in (dp_zip, rp_zip, bundle):
                self.assertTrue(path.exists(), path.name)
            with zipfile.ZipFile(dp_zip) as z:
                self.assertIn('pack.mcmeta', z.namelist())
                self.assertIn('data/minecraft/tags/function/load.json', z.namelist())
            with zipfile.ZipFile(rp_zip) as z:
                self.assertIn('pack.mcmeta', z.namelist())
                self.assertIn('assets/fallen_knight/textures/entity/fallen_knight.png', z.namelist())
            with zipfile.ZipFile(bundle) as z:
                names = set(z.namelist())
                self.assertIn(dp_zip.name, names)
                self.assertIn(rp_zip.name, names)
                self.assertIn('INSTALL.txt', names)


if __name__ == '__main__':
    unittest.main()
