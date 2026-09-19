#!/usr/bin/env python3
"""Validate recorded offscreen measurements. This never certifies native behavior."""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import sys

SCENARIOS = ('realistic', 'heavy', 'stress1000', 'extreme')
TIMINGS = ('total', 'simulation', 'behavior', 'spatial', 'trails', 'instance_prep', 'render_prep', 'gpu')
SOAK_EVENTS = ('config_roundtrips', 'extreme_presets', 'gpu_recreations', 'heavy_presets',
               'independent_modes', 'panic_hides', 'persistent_pauses', 'second_display_draws',
               'topology_changes')


def require(condition: bool, message: str) -> None:
    # Unlike assert, an evidence gate must stay active with python -O.
    if not condition:
        raise ValueError(message)


def number(value, label: str, integer: bool = False):
    require(type(value) in (int, float) and math.isfinite(value) and value >= 0,
            label + ' must be a finite nonnegative number')
    require(not integer or type(value) is int, label + ' must be an integer')
    return value


def distribution(values: list[float]) -> dict | None:
    if not values:
        return None
    ordered = sorted(values)
    n = len(ordered)
    # Match metrics::Distribution::new's documented nearest-rank method.
    return dict(count=n, min=ordered[0], median=ordered[math.ceil(0.5*n)-1],
                p95=ordered[math.ceil(0.95*n)-1], p99=ordered[math.ceil(0.99*n)-1],
                worst=ordered[-1], mean=sum(v/n for v in ordered))


def audit_benchmark(data: dict, scenario: str, profile_sha256: str) -> dict:
    require(data.get('report_schema') == 1, 'unsupported benchmark report schema')
    require(scenario in SCENARIOS and data.get('scenario') == scenario, 'benchmark scenario mismatch')
    require(data.get('mode') == 'offscreen_serialized', 'unsupported measurement mode')
    require(data.get('native_input_verified') is False and data.get('native_60fps_qualified') is False,
            'offscreen evidence cannot qualify native input or frame rate')
    require(data.get('bundled_profile_sha256') == profile_sha256, 'compiled profile checksum mismatch')
    frames = number(data.get('frames'), 'frames', True)
    require(frames >= 600, 'release measurement requires at least 600 frames')
    require(number(data.get('warmup_frames'), 'warmup_frames', True) >= 60,
            'release measurement requires at least 60 warmup frames')
    samples = data.get('samples')
    require(isinstance(samples, list) and len(samples) == frames, 'frame/sample count mismatch')
    populations = []
    timings = {name: [] for name in TIMINGS}
    for i, sample in enumerate(samples):
        require(isinstance(sample, dict), 'frame sample must be an object')
        require(type(sample.get('frame')) is int and sample['frame'] == i,
                'frame indices must be contiguous and unique')
        ants = number(sample.get('ants'), 'population', True)
        populations.append(ants)
        require(number(sample.get('draws'), 'draws', True) == 1,
                'one completed instanced draw is required per benchmark frame')
        require(type(sample.get('simulation_allocations')) is int and sample['simulation_allocations'] == 0,
                'steady-state simulation allocation count must be measured and zero')
        dropped = number(sample.get('dropped_ticks'), 'dropped_ticks', True)
        require(scenario != 'stress1000' or dropped == 0, 'stress1000 dropped simulation ticks')
        number(sample.get('upload_bytes'), 'upload_bytes', True)
        lods = sample.get('lod_counts')
        require(isinstance(lods, list) and len(lods) == 3, 'three LOD counts are required')
        require(sum(number(v, 'LOD count', True) for v in lods) == ants,
                'LOD counts do not match population')
        for name in TIMINGS:
            value = sample.get(name + '_ms')
            if name == 'gpu' and value is None:
                continue  # Timestamp queries are optional; absence is never a fabricated zero.
            timings[name].append(number(value, name + '_ms'))
    summaries = data.get('metrics_ms')
    require(isinstance(summaries, dict), 'missing timing distributions')
    recomputed = {}
    for name, values in timings.items():
        expected = distribution(values)
        actual = summaries.get(name)
        recomputed[name] = expected
        if expected is None:
            require(actual is None, name + ' summary claims missing measurements')
            continue
        require(isinstance(actual, dict), name + ' summary is missing')
        require(number(actual.get('count'), name + '.count', True) == len(values),
                name + ' summary count differs from samples')
        for field in ('min', 'median', 'p95', 'p99', 'worst', 'mean'):
            reported = number(actual.get(field), name + '.' + field)
            require(math.isclose(reported, expected[field], rel_tol=1e-9, abs_tol=1e-9),
                    name + '.' + field + ' differs from recorded samples')
    population = data.get('population')
    require(isinstance(population, dict), 'population summary is missing')
    minimum, maximum = min(populations), max(populations)
    require(population.get('min') == minimum and population.get('max') == maximum,
            'population summary differs from frame samples')
    target = number(population.get('target'), 'population target', True)
    require(0 < minimum <= maximum <= target, 'invalid population range')
    if scenario == 'stress1000':
        require(minimum >= 1000, 'stress1000 population fell below 1000')
    if scenario == 'extreme':
        require(2000 <= minimum and maximum <= 5000, 'extreme population must remain in 2000..5000 range')
    require(data.get('required_population_pass') is True, 'required population flag failed')
    require(number(data.get('last_image_nonzero_alpha_pixels'), 'render coverage', True) > 0,
            'benchmark image contains no rendered creatures')
    p99 = recomputed['total']['p99']
    budget_pass = p99 <= 16.67
    require(data.get('offscreen_frame_budget_16_67ms_p99_pass') is budget_pass,
            'frame budget flag differs from measured p99')
    require(scenario != 'stress1000' or budget_pass, 'stress1000 p99 exceeds the 16.67 ms frame budget')
    return dict(passed=True, scenario=scenario, frames=frames, population_min=minimum,
                total_ms=recomputed['total'], offscreen_frame_budget_pass=budget_pass,
                native_60fps_qualified=False, native_input_verified=False,
                limitations=['Checks recorded completed offscreen work, not native compositor performance.',
                             'Resource trends and physical display behavior require separate review.'])


def audit_soak(data: dict, seconds: int) -> dict:
    require(type(seconds) is int and 1 <= seconds <= 86400, 'requested duration must be 1..86400 seconds')
    require(data.get('report_schema') == 1, 'unsupported soak report schema')
    require(data.get('mode') == 'wall-clock offscreen lifecycle stress', 'unsupported soak mode')
    require(data.get('complete') is True, 'soak did not complete')
    require(data.get('native_input_verified') is False, 'offscreen soak cannot qualify native input')
    require(number(data.get('requested_seconds'), 'requested_seconds', True) == seconds,
            'soak requested duration differs from the invoked gate')
    wall = number(data.get('wall_seconds'), 'wall_seconds')
    require(wall >= seconds, 'measured wall time is shorter than the required duration')
    require(number(data.get('frames'), 'frames', True) > 0, 'soak performed no frames')
    for name in ('hidden_draws', 'hidden_state_changes'):
        require(number(data.get(name), name, True) == 0, name + ' must stay zero')
    two_hours = wall >= 7200
    require(data.get('two_hour_wall_soak_completed') is two_hours,
            'two-hour wall-clock completion flag differs from actual wall time')
    events = data.get('events')
    require(isinstance(events, dict), 'missing lifecycle events')
    if seconds >= 7200:
        for name in SOAK_EVENTS:
            require(number(events.get(name), name, True) > 0, 'required lifecycle event not exercised: ' + name)
    resources = data.get('resources')
    require(isinstance(resources, list) and resources, 'missing resource samples')
    previous = -1.0
    for sample in resources:
        elapsed = number(sample.get('wall_seconds'), 'resource wall_seconds')
        require(previous <= elapsed <= wall, 'resource samples are out of wall-clock order')
        previous = elapsed
    require(isinstance(data.get('process_before'), dict) and isinstance(data.get('process_after'), dict),
            'missing process resource endpoints')
    return dict(passed=True, wall_seconds=wall, requested_seconds=seconds,
                two_hour_wall_soak_completed=two_hours, native_input_verified=False,
                leak_free_qualified=False,
                limitations=['Completion applies to this offscreen interval only.',
                             'Resource samples are retained for trend review; this does not prove absence of leaks.',
                             'Physical displays, native input, installers and OS lifecycle remain separate gates.'])


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON field: ' + key)
        result[key] = value
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='kind', required=True)
    for kind in ('benchmark', 'soak'):
        sub = commands.add_parser(kind)
        sub.add_argument('--input', type=Path, required=True)
        sub.add_argument('--output', type=Path, required=True)
        if kind == 'benchmark':
            sub.add_argument('--scenario', choices=SCENARIOS, required=True)
            sub.add_argument('--profile', type=Path, required=True)
        else:
            sub.add_argument('--seconds', type=int, required=True)
    args = parser.parse_args()
    if args.output.exists() or args.output.resolve() == args.input.resolve():
        parser.error('Choose a new audit output; existing evidence is never overwritten')
    report = dict(schema=1, kind=args.kind, input=str(args.input.resolve()), passed=False,
                  native_input_verified=False, native_60fps_qualified=False)
    try:
        raw = args.input.read_bytes()
        report['input_sha256'] = hashlib.sha256(raw).hexdigest()
        data = json.loads(raw.decode('utf-8'), object_pairs_hook=unique_object)
        require(isinstance(data, dict), 'measurement report must be an object')
        if args.kind == 'benchmark':
            profile_hash = hashlib.sha256(args.profile.read_bytes()).hexdigest()
            report['profile_sha256'] = profile_hash
            report.update(audit_benchmark(data, args.scenario, profile_hash))
        else:
            report.update(audit_soak(data, args.seconds))
    except (OSError, ValueError, TypeError, AttributeError, OverflowError) as error:
        report['error'] = str(error)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + '.tmp')
    temporary.write_text(json.dumps(report, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    os.replace(temporary, args.output)
    print(('PASS' if report['passed'] else 'FAIL') + ': ' + str(args.output))
    if not report['passed']:
        print(report['error'], file=sys.stderr)
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
