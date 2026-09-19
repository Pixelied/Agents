"""Regressions in the verifier, not mocked claims about Rust or native execution.

Unavailable Cargo steps are substituted only to reach the Python comparison and
reporting code. That comparison always executes the real production subprocess.
"""
from __future__ import annotations
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load_runner():
    spec = importlib.util.spec_from_file_location('runner_integrity_test', ROOT / 'scripts/verify_release.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def fixture(root):
    (root / 'app/assets/creature-profiles').mkdir(parents=True)
    (root / 'app/crates/desktop-app/src').mkdir(parents=True)
    (root / 'app/Cargo.toml').write_text('[workspace]\n')
    (root / 'app/crates/desktop-app/src/lib.rs').write_text('pub const VERSION: u32 = 1;\n')
    (root / 'app/assets/creature-profiles/runtime-profiles.bin').write_bytes(b'embedded profile')
    (root / 'app/assets/creature-profiles/build-input.json').write_text('{}\n')


def run_verifier(root, output, compiled=b'embedded profile', mutation=None, optimize=False):
    module = load_runner()
    actual_run_step = module.run_step

    def external_step(name, command, cwd, out, timeout):
        if command[0] == 'cargo':
            if name == 'compile-profiles':
                Path(command[command.index('--output') + 1]).write_bytes(compiled)
                Path(command[command.index('--report') + 1]).write_text('{}\n')
            return {'name': name, 'passed': True, 'log': name + '.log', 'exit_code': 0}
        if name == 'profile-reproducibility' and mutation is not None:
            mutation(root)
        return actual_run_step(name, command, cwd, out, timeout)

    argv = ['verify_release.py', '--root', str(root), '--output', str(output)]
    with patch.object(sys, 'argv', argv), patch.object(module, 'shared_steps', return_value=[]), \
         patch.object(module, 'run_step', side_effect=external_step), \
         patch.dict(os.environ, {'PYTHONOPTIMIZE': '1' if optimize else '0'}), \
         contextlib.redirect_stdout(io.StringIO()):
        return module.main()


class VerificationIntegrityTests(unittest.TestCase):
    def test_optimized_python_cannot_bypass_profile_comparison(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            code = run_verifier(root, root / 'verification/fresh', compiled=b'wrong profile', optimize=True)
            self.assertNotEqual(code, 0, 'Different profile bytes must fail even with PYTHONOPTIMIZE=1')

    def test_matching_profiles_still_pass_under_optimized_python(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            self.assertEqual(run_verifier(root, root / 'verification/fresh', optimize=True), 0)

    def test_stale_output_is_rejected_without_overwriting_its_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            output = root / 'verification/old'
            output.mkdir(parents=True)
            old = output / 'verification.json'
            old.write_text('historical evidence\n')
            with self.assertRaisesRegex(ValueError, 'empty|fresh'):
                run_verifier(root, output)
            self.assertEqual(old.read_text(), 'historical evidence\n')

    def test_source_change_during_verification_invalidates_a_green_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            output = root / 'verification/fresh'
            def mutate(p):
                (p / 'app/crates/desktop-app/src/lib.rs').write_text('pub const VERSION: u32 = 2;\n')
            code = run_verifier(root, output, mutation=mutate)
            self.assertNotEqual(code, 0, 'Tests cannot qualify a source tree modified after they ran')
            report = json.loads((output / 'verification.json').read_text())
            self.assertFalse(report['source_unchanged'])
            self.assertIn('app/crates/desktop-app/src/lib.rs', report['changed_source_paths'])

    def test_source_directory_named_verification_is_not_excluded_like_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            source = root / 'app/crates/verification/src/lib.rs'
            source.parent.mkdir(parents=True)
            source.write_text('pub fn check() {}\n')
            def mutate(p):
                source.write_text('pub fn check() { panic!("changed"); }\n')
            self.assertNotEqual(run_verifier(root, root / 'verification/fresh', mutation=mutate), 0)

    def test_build_cache_and_evidence_writes_do_not_invalidate_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            def mutate(p):
                cache = p / 'app/target/cache'
                cache.parent.mkdir(parents=True)
                cache.write_bytes(b'normal compiler output')
            self.assertEqual(run_verifier(root, root / 'verification/fresh', mutation=mutate), 0)


if __name__ == '__main__':
    unittest.main()
