#!/usr/bin/env python3
"""Run reproducible gates and keep real logs. Passing automation never implies native input acceptance."""
from __future__ import annotations
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time


def find_project(start: Path) -> Path:
    start = start.resolve()
    if (start / 'app/Cargo.toml').is_file():
        return start
    # Locate the already-selected checkout, not a canonical branch by its name.
    candidates = sorted(p.parent.parent.resolve()
                        for p in (start / 'projects').glob('*/app/Cargo.toml')
                        if p.is_file() and p.parent.parent.resolve().is_relative_to(start))
    if len(candidates) != 1:
        raise ValueError('Select one application root explicitly; found ' + str(len(candidates)) + ' matching projects')
    return candidates[0]


# Exclude generated evidence/caches, never test or application source files.
IGNORED_SOURCE_DIRECTORIES = {'.git', '__pycache__', '.pytest_cache', '.venv'}


def source_snapshot(root: Path, output: Path) -> dict[str, str]:
    """Hash source bytes and executable bits at each end of the verification run.

    This is an endpoint comparison, not a claim to detect changes reverted while
    the checks are running. Run in an isolated checkout for that guarantee.
    """
    ignored_paths = {output.resolve(), root / 'verification', root / 'dist',
                     root / 'target', root / 'app/target'}
    target = os.environ.get('CARGO_TARGET_DIR')
    if target:
        target_path = Path(target)
        ignored_paths.add((target_path if target_path.is_absolute() else root / 'app' / target_path).resolve())
    manifest = {}
    for directory, dirs, files in os.walk(root, followlinks=False):
        parent = Path(directory)
        dirs[:] = sorted(name for name in dirs
                         if name not in IGNORED_SOURCE_DIRECTORIES
                         and (parent / name).resolve() not in ignored_paths)
        # A symlinked source tree could change outside the monitored root. Refuse
        # to qualify it rather than following arbitrary filesystem targets.
        for name in dirs + files:
            path = parent / name
            if name == '.git' or path.resolve() in ignored_paths:
                continue
            if path.is_symlink():
                raise ValueError('Source symlink is not supported by verification: ' + str(path))
        for name in sorted(files):
            path = parent / name
            if name == '.git' or path.suffix == '.pyc' or path.resolve() in ignored_paths:
                continue
            digest = hashlib.sha256()
            digest.update(b'executable\0' if path.stat().st_mode & 0o111 else b'regular\0')
            with path.open('rb') as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(block)
            manifest[path.relative_to(root).as_posix()] = digest.hexdigest()
    return manifest


def snapshot_digest(manifest: dict[str, str]) -> str:
    encoded = json.dumps(manifest, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(encoded).hexdigest()


def run_step(name: str, command: list[str], cwd: Path, output: Path, timeout: float) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    path = output / (name + '.log')
    begin = time.monotonic()
    code = None
    failure = None
    with path.open('w', encoding='utf-8') as log:
        log.write('COMMAND: ' + json.dumps(command) + '\nCWD: ' + str(cwd) + '\n')
        log.flush()
        try:
            result = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT, timeout=timeout, check=False)
            code = result.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            failure = str(error)
            log.write('\nRUNNER_ERROR: ' + failure + '\n')
        log.write('\nEXIT_CODE: ' + str(code) + '\n')
    return {'name': name, 'command': command, 'exit_code': code, 'passed': code == 0 and failure is None,
            'seconds': time.monotonic() - begin, 'log': path.name,
            'log_sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'runner_error': failure}


def save_report(path: Path, report: dict) -> None:
    temporary = path.with_suffix('.json.tmp')
    temporary.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    os.replace(temporary, path)


def shared_steps(root: Path, app: Path, locked: list[str]) -> list:
    """Host-independent gates, including command tests that do not open native windows."""
    return [
        ('fmt', ['cargo', 'fmt', '--all', '--', '--check'], app, 300),
        ('clippy', ['cargo', 'clippy', '--workspace', '--all-targets', '--all-features'] + locked + ['--', '-D', 'warnings'], app, 1800),
        ('packaging-tests', [sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts/tests', '-v'], root, 120),
        ('input-probe-tests', ['cargo', 'test', '-p', 'desktop-app', '--all-features', '--example', 'input_probe', '--release'] + locked, app, 1800),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path)
    parser.add_argument('--input', type=Path, help='Original Mega Pack root, or its audited build-input JSON')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--gpu', action='store_true', help='Run the full GPU-dependent suite and visual scenes; do not use on GPU-less hosts')
    parser.add_argument('--benchmark', action='store_true', help='Run all four completed-GPU-work scenarios; requires --gpu')
    parser.add_argument('--package', action='store_true', help='Run actual native packaging on macOS or Windows')
    parser.add_argument('--soak-seconds', type=int, default=0)
    args = parser.parse_args()
    if args.benchmark and not args.gpu:
        parser.error('--benchmark requires --gpu')
    if args.soak_seconds < 0 or args.soak_seconds > 86400 or (args.soak_seconds and not args.gpu):
        parser.error('--soak-seconds must be 1..86400 with --gpu, or zero to omit')
    root = find_project(args.root)
    output = (args.output or root / 'verification/final').resolve()
    if root.is_relative_to(output):
        raise ValueError('Verification output must not contain the source root; choose a fresh output directory')
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError('Verification output must be empty; choose a fresh --output directory: ' + str(output))
    output.mkdir(parents=True, exist_ok=True)
    source_before = source_snapshot(root, output)
    app = root / 'app'
    locked = ['--locked'] + (['--offline'] if args.offline else [])
    input_path = (args.input or app / 'assets/creature-profiles/build-input.json').resolve()
    profile = output / 'compiled-profiles.bin'
    profile_report = output / 'compiled-profiles.json'
    report = {'schema': 1, 'started_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'host': platform.platform(), 'source_root': str(root), 'input': str(input_path),
              'source_before_sha256': snapshot_digest(source_before),
              'source_unchanged': None, 'changed_source_paths': [],
              'gpu_tests_requested': args.gpu, 'steps': [], 'complete': False,
              'all_requested_automated_gates_pass': False, 'native_input_verified': False,
              'physical_display_verified': False, 'signed_release_qualified': False}
    save_report(output / 'source-before.json', source_before)
    try:
        report['commit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True, stderr=subprocess.DEVNULL).strip()
        report['working_tree'] = subprocess.check_output(['git', 'status', '--short'], cwd=root, text=True, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        report['commit'] = None
        report['working_tree'] = 'Source archive without git metadata'
    steps = shared_steps(root, app, locked)
    if args.gpu:
        steps.append(('release-tests', ['cargo', 'test', '--workspace', '--all-features', '--release'] + locked, app, 1800))
    else:
        core = ['creature-profile', 'display-model', 'settings', 'simulation', 'platform-api', 'platform-macos', 'platform-windows', 'profile-compiler']
        steps.append(('core-release-tests', ['cargo', 'test', '--release'] + locked + [x for p in core for x in ['-p', p]], app, 1800))
        steps.append(('controller-release-tests', ['cargo', 'test', '-p', 'desktop-app', '--no-default-features', '--release'] + locked, app, 1800))
    steps += [
        ('compile-profiles', ['cargo', 'run', '-p', 'profile-compiler', '--release'] + locked + ['--', 'compile', '--input', str(input_path), '--output', str(profile), '--report', str(profile_report)], app, 1800),
        ('verify-profiles', ['cargo', 'run', '-p', 'profile-compiler', '--release'] + locked + ['--', 'verify', '--bundle', str(profile)], app, 1800),
        ('profile-reproducibility', [sys.executable, '-c', 'from pathlib import Path\nimport sys\na,b=map(Path,sys.argv[1:])\nif a.read_bytes()!=b.read_bytes():\n    raise SystemExit("profile differs from compiled-in bundle")\nprint("exact profile bytes match")', str(profile), str(app / 'assets/creature-profiles/runtime-profiles.bin')], app, 120),
    ]
    if args.gpu:
        steps += [
            ('visuals', ['cargo', 'run', '-p', 'rendering', '--example', 'validation', '--release'] + locked + ['--', '--scene', 'all', '--output', str(output / 'visuals')], app, 1800),
            ('settings-visuals', ['cargo', 'run', '-p', 'desktop-app', '--release'] + locked + ['--', '--validate-ui', str(output / 'settings-visuals')], app, 1800),
        ]
    if args.benchmark:
        for scenario in ('realistic', 'heavy', 'stress1000', 'extreme'):
            steps.append(('benchmark-' + scenario, ['cargo', 'run', '-p', 'desktop-app', '--release', '--features', 'allocation-metrics'] + locked + ['--', '--benchmark-scenario', scenario, '--json', str(output / ('bench-' + scenario + '.json'))], app, 1800))
            steps.append(('audit-benchmark-' + scenario, [sys.executable, str(root / 'scripts/verify_measurements.py'),
                'benchmark', '--input', str(output / ('bench-' + scenario + '.json')), '--scenario', scenario,
                '--profile', str(app / 'assets/creature-profiles/runtime-profiles.bin'),
                '--output', str(output / ('audit-bench-' + scenario + '.json'))], root, 120))
    if args.soak_seconds:
        steps.append(('soak', ['cargo', 'run', '-p', 'desktop-app', '--release', '--features', 'allocation-metrics'] + locked + ['--', '--soak-seconds', str(args.soak_seconds), '--json', str(output / 'soak.json')], app, args.soak_seconds + 1800))
        steps.append(('audit-soak', [sys.executable, str(root / 'scripts/verify_measurements.py'), 'soak',
            '--input', str(output / 'soak.json'), '--seconds', str(args.soak_seconds),
            '--output', str(output / 'audit-soak.json')], root, 120))
    if args.package:
        if platform.system() == 'Darwin':
            command = ['bash', str(app / 'packaging/macos/build-dmg.sh')]
        elif platform.system() == 'Windows':
            command = ['pwsh', '-NoProfile', '-File', str(app / 'packaging/windows/build-msi.ps1')]
        else:
            command = [sys.executable, '-c', 'raise SystemExit("Native packaging requires macOS or Windows with its SDK")']
        steps.append(('native-packaging', command, root, 3600))
    for name, command, cwd, timeout in steps:
        result = run_step(name, command, cwd, output, timeout)
        report['steps'].append(result)
        save_report(output / 'verification.json', report)
        print(name + ': ' + ('PASS' if result['passed'] else 'FAIL') + ' (' + str(output / result['log']) + ')', flush=True)
        if not result['passed']:
            report['stopped_after_failure'] = name
            break
    try:
        source_after = source_snapshot(root, output)
        save_report(output / 'source-after.json', source_after)
        report['source_after_sha256'] = snapshot_digest(source_after)
        report['changed_source_paths'] = sorted(
            name for name in source_before.keys() | source_after.keys()
            if source_before.get(name) != source_after.get(name))
        report['source_unchanged'] = not report['changed_source_paths']
    except (OSError, ValueError) as error:
        report['source_unchanged'] = False
        report['source_verification_error'] = str(error)
    report['complete'] = len(report['steps']) == len(steps)
    report['all_requested_automated_gates_pass'] = (report['complete']
        and report['source_unchanged'] and all(s['passed'] for s in report['steps']))
    if not report['source_unchanged']:
        print('source-integrity: FAIL (source changed or could not be rechecked)', flush=True)
    report['finished_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    save_report(output / 'verification.json', report)
    return 0 if report['all_requested_automated_gates_pass'] else 1


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        raise SystemExit('Verification setup failed: ' + str(error))
