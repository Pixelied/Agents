"""Analyze explicitly calibrated tracks without certifying a research source.

This command writes to a NEW output directory, never the canonical biology
folder by default. A corpus reviewer must validate the source and license
before promoting its output into the distributable research database.
"""
import argparse
import csv
import json
from pathlib import Path
import sys

# Also support direct execution from a clean source checkout.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

import numpy as np
import pandas as pd

from insect_research.checksums import sha256_file
from insect_research.distributions import quantile_summary
from insect_research.trajectories import normalize_track, stop_bouts, summarize_track


def _distribution(values):
    finite = np.asarray(values, dtype=float)
    return quantile_summary(finite) if np.isfinite(finite).any() else None


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--species-id", required=True)
    parser.add_argument("--behavior-state", required=True)
    parser.add_argument("--calibration-notes", required=True)
    parser.add_argument("--stop-threshold-mm-s", type=float, required=True,
                        help="Explicit analysis threshold, NOT an inferred biological constant")
    parser.add_argument("--max-gap-s", type=float)
    parser.add_argument("--movement-epsilon-mm", type=float, default=0.0)
    parser.add_argument("--min-stop-duration-s", type=float, default=0.0)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        for name in ("source_id", "species_id", "behavior_state", "calibration_notes"):
            if not getattr(args, name).strip():
                raise ValueError(f"{name} must not be blank")
        if args.output_dir.exists():
            raise ValueError(f"output directory already exists: {args.output_dir}")
        raw = pd.read_csv(args.input, dtype={"track_id": str})
        for column, expected in (("species_id", args.species_id),
                                 ("behavior_state", args.behavior_state)):
            if column in raw and (raw[column].isna().any() or set(raw[column]) != {expected}):
                raise ValueError(f"{column}: mixed or mismatched population; split the input explicitly")
        normalized = normalize_track(raw, max_gap_s=args.max_gap_s,
                                     movement_epsilon_mm=args.movement_epsilon_mm)
        bouts = stop_bouts(normalized, speed_threshold_mm_s=args.stop_threshold_mm_s,
                           min_duration_s=args.min_stop_duration_s)
        complete = bouts.loc[~(bouts.left_censored.astype(bool) | bouts.right_censored.astype(bool))]
        populations = (
            ("walking_speed.csv", "walking_speed", "mm/s", normalized.speed_mm_s),
            ("acceleration.csv", "signed_speed_acceleration", "mm/s^2", normalized.acceleration_mm_s2),
            ("turn_parameters.csv", "absolute_turn_rate", "rad/s", normalized.turn_rate_rad_s.abs()),
            ("stop_durations.csv", "complete_stop_duration", "s", complete.duration_s),
        )
        distributions = {parameter: _distribution(values) for _, parameter, _, values in populations}
        payload = {
            "source_id": args.source_id, "species_id": args.species_id,
            "behavior_state": args.behavior_state,
            "input_sha256": sha256_file(args.input),
            "calibration_notes": args.calibration_notes,
            "acceptance_status": "analysis-only; source/license review required",
            "settings": {"stop_threshold_mm_s": args.stop_threshold_mm_s,
                         "max_gap_s": args.max_gap_s,
                         "movement_epsilon_mm": args.movement_epsilon_mm,
                         "min_stop_duration_s": args.min_stop_duration_s},
            "summary": summarize_track(normalized), "distributions": distributions,
            "stops": {"observed_bout_count": len(bouts), "complete_bout_count": len(complete),
                      "censored_bout_count": len(bouts) - len(complete)},
            "limitations": ["Intervals are not independent animals.",
                "Pooled quantiles are sample-weighted, not time- or animal-weighted.",
                "Centroid heading is not anatomical body heading.",
                "No smoothing or measurement-error correction was inferred.",
                "Censored pauses are excluded from complete-duration quantiles."],
        }
        text = json.dumps(payload, indent=2, sort_keys=True, allow_nan=False) + "\n"
        args.output_dir.mkdir(parents=True, exist_ok=False)
        normalized.to_csv(args.output_dir / "normalized_tracks.csv", index=False)
        bouts.to_csv(args.output_dir / "stop_bouts.csv", index=False)
        (args.output_dir / "analysis.json").write_text(text, encoding="utf-8")
        for filename, parameter, unit, values in populations:
            dist = distributions[parameter]
            row = {"species_id": args.species_id, "behavior_state": args.behavior_state,
                   "parameter": parameter, "unit": unit,
                   "sample_count": dist["count"] if dist else 0,
                   "track_count": payload["summary"]["track_count"],
                   "source_ids": args.source_id, "input_sha256": payload["input_sha256"],
                   "calibration_notes": args.calibration_notes,
                   "analysis_settings": json.dumps(payload["settings"], sort_keys=True),
                   "acceptance_status": payload["acceptance_status"]}
            for metric in ("mean", "sd", "min", "p05", "p25", "median", "p75", "p95", "max"):
                row[metric] = dist[metric] if dist else ""
            with (args.output_dir / filename).open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(row))
                writer.writeheader()
                writer.writerow(row)
        print(text, end="")
        return 0
    except (ValueError, OSError, KeyError, TypeError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    raise SystemExit(main())
