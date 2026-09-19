"""Exercise the real workflow locator and release-root selection without a runner."""
from __future__ import annotations
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/insect-release.yml'


def project(root: Path, name: str = '.') -> Path:
    p = root / name
    (p / 'app').mkdir(parents=True, exist_ok=True)
    (p / 'app/Cargo.toml').write_text('[workspace]\n', encoding='utf-8')
    return p


def locate(root: Path, requested: str = '') -> subprocess.CompletedProcess:
    workflow = WORKFLOW.read_text(encoding='utf-8')
    block = workflow.split('      - name: Locate the single intended project\n', 1)[1]
    code = block.split("          python - <<'PY'\n", 1)[1].split('\n          PY\n', 1)[0]
    env = dict(os.environ, RUNNER_TEMP=str(root / 'runner temp'),
               GITHUB_ENV=str(root / 'env.txt'), GITHUB_OUTPUT=str(root / 'output.txt'),
               REQUESTED_PROJECT_ROOT=requested)
    return subprocess.run([sys.executable, '-c', textwrap.dedent(code)], cwd=root,
                          env=env, text=True, capture_output=True, check=False)


class WorkflowContinuationTests(unittest.TestCase):
    def test_job_environment_does_not_reference_unavailable_runner_context(self):
        # GitHub's contexts reference excludes runner from jobs.<job_id>.env.
        workflow = WORKFLOW.read_text(encoding='utf-8')
        blocks = re.findall(r'(?m)^    env:\n((?:^      .*\n)*)', workflow)
        self.assertTrue(blocks)
        for block in blocks:
            self.assertNotRegex(block, r'\$\{\{\s*runner(?:\.|\[)')

    def test_real_locator_emits_runner_paths_with_spaces(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project(root)
            result = locate(root)
            self.assertEqual(result.returncode, 0, result.stderr)
            values = dict(line.split('=', 1) for line in (root / 'env.txt').read_text().splitlines())
            self.assertIn('CARGO_TARGET_DIR', values)
            self.assertEqual(Path(values['CARGO_TARGET_DIR']), root / 'runner temp/insect-target')
            self.assertIn('VERIFICATION_ROOT', values)
            self.assertEqual(Path(values['VERIFICATION_ROOT']), root / 'runner temp/insect-verification')

    def test_actual_locator_supports_current_nonlegacy_project_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            selected = project(root, 'projects/insect-realism-native-n8e6')
            result = locate(root)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('root=' + selected.as_posix(), (root / 'output.txt').read_text())

    def test_explicit_project_disambiguates_without_picking_a_branch_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project(root, 'projects/insect-realism-desktop-app')
            selected = project(root, 'projects/insect-realism-desktop-app-recovery')
            result = locate(root, 'projects/insect-realism-desktop-app-recovery')
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('root=' + selected.as_posix(), (root / 'output.txt').read_text())

    def test_override_cannot_escape_checkout(self):
        with tempfile.TemporaryDirectory() as tmp:
            parent = Path(tmp)
            root = parent / 'checkout'
            root.mkdir()
            project(root)
            project(parent, 'outside')
            result = locate(root, '../outside')
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse((root / 'env.txt').exists())

    def test_artifact_upload_cannot_expand_a_missing_project_output_to_root(self):
        text = WORKFLOW.read_text(encoding='utf-8')
        upload = text.split('      - name: Preserve actual logs and produced packages\n', 1)[1].split('\n  draft-release:', 1)[0]
        self.assertIn("if: always() && steps.project.outcome == 'success'", upload)

    def test_release_runner_discovers_nonlegacy_project_root(self):
        spec = importlib.util.spec_from_file_location('runner_ci_test', ROOT / 'scripts/verify_release.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            selected = project(root, 'projects/insect-realism-native-n8e6')
            self.assertEqual(module.find_project(root), selected)


if __name__ == '__main__':
    unittest.main()
