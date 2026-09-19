"""Test measured release gates with explicit synthetic reports, not GPU mocks."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROFILE = hashlib.sha256(b'profile fixture').hexdigest()


def benchmark(scenario='stress1000', total_ms=10.0, ants=1200):
    names = ('total', 'simulation', 'behavior', 'spatial', 'trails', 'instance_prep', 'render_prep', 'gpu')
    values = {name: total_ms if name == 'total' else 0.1 for name in names}
    metrics = {name: dict(count=600, min=value, median=value, p95=value, p99=value,
                         worst=value, mean=value) for name, value in values.items()}
    return dict(report_schema=1, mode='offscreen_serialized', scenario=scenario,
                frames=600, warmup_frames=60, bundled_profile_sha256=PROFILE,
                native_input_verified=False, native_60fps_qualified=False,
                offscreen_frame_budget_16_67ms_p99_pass=total_ms <= 16.67,
                required_population_pass=True, last_image_nonzero_alpha_pixels=100,
                population=dict(min=ants, max=ants, target=ants), metrics_ms=metrics,
                samples=[dict(frame=i, ants=ants, draws=1, dropped_ticks=0,
                              upload_bytes=ants*96, lod_counts=[ants, 0, 0],
                              simulation_allocations=0,
                              **{name+'_ms': value for name, value in values.items()}) for i in range(600)])


def soak(seconds=7200.1):
    return dict(report_schema=1, mode='wall-clock offscreen lifecycle stress',
                requested_seconds=7200, wall_seconds=seconds, simulated_seconds=80000.0,
                complete=True, frames=600, native_input_verified=False,
                two_hour_wall_soak_completed=seconds >= 7200,
                hidden_draws=0, hidden_state_changes=0,
                events={name: 1 for name in ('config_roundtrips', 'extreme_presets', 'gpu_recreations',
                       'heavy_presets', 'independent_modes', 'panic_hides', 'persistent_pauses',
                       'second_display_draws', 'topology_changes')},
                process_before={'resident_bytes': 1000}, process_after={'resident_bytes': 1500},
                resources=[{'wall_seconds': 0.1}, {'wall_seconds': max(0.1, seconds - 0.1)}])


class MeasurementGateTests(unittest.TestCase):
    def setUp(self):
        path = ROOT / 'scripts/verify_measurements.py'
        self.assertTrue(path.is_file(), 'Measured performance and duration need an executable audit gate')
        spec = importlib.util.spec_from_file_location('measurement_gate_test', path)
        self.audit = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.audit)

    def test_valid_offscreen_run_never_qualifies_native_fps(self):
        result = self.audit.audit_benchmark(benchmark(), 'stress1000', PROFILE)
        self.assertTrue(result['passed'])
        self.assertFalse(result['native_60fps_qualified'])
        self.assertFalse(result['native_input_verified'])

    def test_slow_stress_cannot_pass_using_a_true_summary_flag(self):
        data = benchmark(total_ms=25.0)
        data['offscreen_frame_budget_16_67ms_p99_pass'] = True
        with self.assertRaisesRegex(ValueError, 'budget'):
            self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_extreme_population_is_not_required_to_meet_the_60fps_budget(self):
        result = self.audit.audit_benchmark(benchmark('extreme', 50.0, 5000), 'extreme', PROFILE)
        self.assertTrue(result['passed'])
        self.assertFalse(result['offscreen_frame_budget_pass'])

    def test_underpopulation_cannot_pass_using_a_true_summary_flag(self):
        with self.assertRaisesRegex(ValueError, 'population'):
            self.audit.audit_benchmark(benchmark(ants=999), 'stress1000', PROFILE)

    def test_missing_or_nonzero_simulation_allocations_fail(self):
        for value in (None, 1, -1, True):
            with self.subTest(value=value):
                data = benchmark()
                data['samples'][0]['simulation_allocations'] = value
                with self.assertRaisesRegex(ValueError, 'allocation'):
                    self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_forged_percentile_is_recomputed_from_samples(self):
        data = benchmark()
        data['metrics_ms']['total']['p99'] = 1.0
        with self.assertRaisesRegex(ValueError, 'p99'):
            self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_nonfinite_sample_fails(self):
        data = benchmark()
        data['samples'][0]['total_ms'] = float('nan')
        with self.assertRaisesRegex(ValueError, 'finite'):
            self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_wrong_compiled_profile_fails(self):
        with self.assertRaisesRegex(ValueError, 'profile'):
            self.audit.audit_benchmark(benchmark(), 'stress1000', '0'*64)

    def test_duplicate_frame_cannot_inflate_sample_count(self):
        data = benchmark()
        data['samples'][1]['frame'] = 0
        with self.assertRaisesRegex(ValueError, 'frame'):
            self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_lod_totals_and_instanced_draws_are_checked(self):
        for key, value in (('lod_counts', [0, 0, 0]), ('draws', 1200), ('dropped_ticks', 1)):
            with self.subTest(key=key):
                data = benchmark()
                data['samples'][0][key] = value
                with self.assertRaises(ValueError):
                    self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_insufficient_warmup_and_frames_fail(self):
        for key, value in (('warmup_frames', 0), ('frames', 5)):
            with self.subTest(key=key):
                data = benchmark()
                data[key] = value
                with self.assertRaises(ValueError):
                    self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_native_qualification_claim_from_offscreen_data_is_rejected(self):
        data = benchmark()
        data['native_60fps_qualified'] = True
        with self.assertRaisesRegex(ValueError, 'native'):
            self.audit.audit_benchmark(data, 'stress1000', PROFILE)

    def test_complete_two_hour_wall_clock_soak_passes_without_leak_claim(self):
        result = self.audit.audit_soak(soak(), 7200)
        self.assertTrue(result['passed'])
        self.assertTrue(result['two_hour_wall_soak_completed'])
        self.assertFalse(result['leak_free_qualified'])

    def test_simulated_time_cannot_replace_two_hour_wall_clock_evidence(self):
        data = soak(600)
        data['two_hour_wall_soak_completed'] = True
        with self.assertRaisesRegex(ValueError, 'wall'):
            self.audit.audit_soak(data, 7200)

    def test_short_soak_can_pass_its_actual_interval_without_becoming_two_hours(self):
        data = soak(600.1)
        data['requested_seconds'] = 600
        result = self.audit.audit_soak(data, 600)
        self.assertTrue(result['passed'])
        self.assertFalse(result['two_hour_wall_soak_completed'])

    def test_incomplete_soak_and_hidden_submissions_fail(self):
        for key, value in (('complete', False), ('hidden_draws', 1), ('hidden_state_changes', 1)):
            with self.subTest(key=key):
                data = soak()
                data[key] = value
                with self.assertRaises(ValueError):
                    self.audit.audit_soak(data, 7200)

    def test_full_soak_requires_lifecycle_events(self):
        data = soak()
        data['events']['gpu_recreations'] = 0
        with self.assertRaisesRegex(ValueError, 'gpu_recreations'):
            self.audit.audit_soak(data, 7200)

    def test_cli_failure_saves_an_honest_hashed_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/'input.json'
            output = root/'audit.json'
            source.write_text(json.dumps(soak(10)))
            result = subprocess.run([sys.executable, str(ROOT/'scripts/verify_measurements.py'),
                                     'soak', '--input', str(source), '--output', str(output),
                                     '--seconds', '7200'], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            report = json.loads(output.read_text())
            self.assertFalse(report['passed'])
            self.assertEqual(report['input_sha256'], hashlib.sha256(source.read_bytes()).hexdigest())


class ReleaseRunnerMeasurementIntegrationTests(unittest.TestCase):
    def run_fake_gpu_producer(self, root, slow_stress=False, soak_duration=None):
        """Only the GPU/Cargo producers are substituted; audits run as real subprocesses."""
        import contextlib
        import io
        from unittest.mock import patch
        spec = importlib.util.spec_from_file_location('measurement_runner_integration', ROOT/'scripts/verify_release.py')
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        app = root/'app'
        (app/'assets/creature-profiles').mkdir(parents=True)
        (app/'Cargo.toml').write_text('[workspace]\n')
        (app/'assets/creature-profiles/runtime-profiles.bin').write_bytes(b'profile fixture')
        (app/'assets/creature-profiles/build-input.json').write_text('{}\n')
        # The runner executes the actual script path in the selected checkout.
        (root/'scripts').mkdir()
        (root/'scripts/verify_measurements.py').write_bytes((ROOT/'scripts/verify_measurements.py').read_bytes())
        output = root/'verification/fresh'
        commands = []
        actual = runner.run_step
        def produce(name, command, cwd, out, timeout):
            commands.append(name)
            if command[0] != 'cargo':
                return actual(name, command, cwd, out, timeout)
            if name == 'compile-profiles':
                Path(command[command.index('--output')+1]).write_bytes(b'profile fixture')
            elif name.startswith('benchmark-'):
                scenario = name[len('benchmark-'):]
                total = 25.0 if slow_stress and scenario == 'stress1000' else 10.0
                ants = dict(realistic=50, heavy=500, stress1000=1200, extreme=5000)[scenario]
                Path(command[-1]).write_text(json.dumps(benchmark(scenario, total, ants)))
            elif name == 'soak':
                data = soak(soak_duration + 0.1)
                data['requested_seconds'] = soak_duration
                Path(command[-1]).write_text(json.dumps(data))
            return dict(name=name, passed=True, log=name+'.log', exit_code=0)
        argv = ['verify_release.py', '--root', str(root), '--output', str(output), '--gpu', '--benchmark']
        if soak_duration is not None:
            argv += ['--soak-seconds', str(soak_duration)]
        with patch.object(sys, 'argv', argv), patch.object(runner, 'shared_steps', return_value=[]), \
             patch.object(runner, 'run_step', side_effect=produce), contextlib.redirect_stdout(io.StringIO()):
            code = runner.main()
        return code, commands, json.loads((output/'verification.json').read_text())

    def test_exit_zero_gpu_producer_with_slow_stress_is_not_a_green_release_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, commands, report = self.run_fake_gpu_producer(Path(tmp), slow_stress=True)
            self.assertNotEqual(code, 0, 'The release driver must inspect results, not just producer exit status')
            self.assertEqual(report.get('stopped_after_failure'), 'audit-benchmark-stress1000')

    def test_all_measurement_audits_are_required_and_follow_their_producers(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, commands, report = self.run_fake_gpu_producer(Path(tmp), soak_duration=600)
            self.assertEqual(code, 0)
            for producer in ('benchmark-realistic', 'benchmark-heavy', 'benchmark-stress1000', 'benchmark-extreme', 'soak'):
                self.assertIn('audit-'+producer, commands)
                self.assertEqual(commands.index('audit-'+producer), commands.index(producer)+1)

    def test_optional_two_hour_workflow_also_audits_its_wall_clock_report(self):
        text = (ROOT/'.github/workflows/insect-release.yml').read_text()
        step = text.split('      - name: Optional two-hour wall-clock soak\n', 1)[1].split('      - name:', 1)[0]
        self.assertIn('verify_measurements.py', step)
        self.assertIn('--seconds 7200', step)


if __name__ == '__main__':
    unittest.main()
