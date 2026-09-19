from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

class ProjectIgnoreTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('git'), 'git is required for repository ignore semantics')
    def test_application_lockfile_overrides_workspace_runtime_lock_ignore(self):
        project = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / '.gitignore').write_text('*.lock\n')
            destination = root / 'projects/app'
            (destination / 'app').mkdir(parents=True)
            shutil.copyfile(project / '.gitignore', destination / '.gitignore')
            (destination / 'app/Cargo.lock').write_text('pinned dependencies\n')
            (destination / 'runtime.lock').write_text('ephemeral lease\n')
            command = ['git', '-C', str(root), 'check-ignore', '-q', '--no-index']
            self.assertEqual(subprocess.run(command + ['projects/app/app/Cargo.lock']).returncode, 1,
                             'application Cargo.lock must be trackable despite shared *.lock rule')
            self.assertEqual(subprocess.run(command + ['projects/app/runtime.lock']).returncode, 0)

if __name__ == '__main__':
    unittest.main()
