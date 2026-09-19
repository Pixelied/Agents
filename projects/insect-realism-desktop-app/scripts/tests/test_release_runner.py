"""The release runner must preserve real subprocess failures and reject ambiguous project trees."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

def load_runner():
    path = ROOT / 'scripts/verify_release.py'
    spec = importlib.util.spec_from_file_location('verify_release', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class ReleaseRunnerTests(unittest.TestCase):
    def test_runner_exists(self):
        self.assertTrue((ROOT / 'scripts/verify_release.py').is_file())

    def test_failed_real_subprocess_is_never_a_pass(self):
        module = load_runner()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            result = module.run_step('intentional-failure', [sys.executable, '-c', 'print("regression probe"); raise SystemExit(7)'], root, root, 10)
            self.assertFalse(result['passed'])
            self.assertEqual(result['exit_code'], 7)
            self.assertIn('regression probe', (root / result['log']).read_text())

    def test_input_probe_tests_are_not_silently_omitted(self):
        module = load_runner()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            steps = {name: (command, cwd, timeout) for name, command, cwd, timeout in
                     module.shared_steps(root, root / 'app', ['--locked', '--offline'])}
            self.assertIn('input-probe-tests', steps)
            command, cwd, timeout = steps['input-probe-tests']
            self.assertEqual(command, ['cargo', 'test', '-p', 'desktop-app', '--all-features',
                                      '--example', 'input_probe', '--release', '--locked', '--offline'])
            self.assertEqual(cwd, root / 'app')
            self.assertGreater(timeout, 0)

    def test_tag_release_filter_does_not_disable_normal_branch_push_ci(self):
        # GitHub does not run branch push events when only tags are selected.
        # Inspect the actual workflow, not a simulated event dispatcher.
        workflow = (ROOT / '.github/workflows/insect-release.yml').read_text(encoding='utf-8')
        push = workflow.split('  push:\n', 1)[1].split('\n  pull_request:', 1)[0]
        self.assertIn("    branches: ['**']", push)
        self.assertIn("    tags: ['insect-desktop-v*']", push)
        self.assertIn("      - 'app/**'", push)
        self.assertIn("      - 'projects/insect-realism-desktop-app/**'", push)

    def test_nested_project_selection_is_unambiguous(self):
        module = load_runner()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            selected = root / 'projects/insect-realism-desktop-app'
            (selected / 'app').mkdir(parents=True)
            (selected / 'app/Cargo.toml').write_text('[workspace]')
            self.assertEqual(module.find_project(root), selected)
            other = root / 'projects/insect-realism-desktop-app-recovery'
            (other / 'app').mkdir(parents=True)
            (other / 'app/Cargo.toml').write_text('[workspace]')
            with self.assertRaises(ValueError):
                module.find_project(root)

if __name__ == '__main__':
    unittest.main()
