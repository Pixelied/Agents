"""Calibrated planar trajectory analysis, with explicit missing observations.

Coordinates must already be in millimeters and time in seconds. This module
never estimates scale, frame rate, smoothing, gaps, or biological stop thresholds.
Per-step speeds are interval averages; their derivatives use interval midpoints.
"""
import math

import numpy as np
import pandas as pd

_REQUIRED = ("track_id", "time_s", "x_mm", "y_mm")
_STOP_COLUMNS = ("track_id", "segment_id", "start_time_s", "end_time_s",
                 "duration_s", "interval_count", "left_censored", "right_censored")


def _finite_setting(name: str, value: float, *, positive: bool = False) -> float:
    number = float(value)
    if not math.isfinite(number) or number < 0 or (positive and number == 0):
        raise ValueError(f"{name} must be finite and {'positive' if positive else 'non-negative'}")
    return number


def normalize_track(
    df: pd.DataFrame, *, max_gap_s: float | None = None,
    movement_epsilon_mm: float = 0.0,
) -> pd.DataFrame:
    """Return a sorted copy with per-interval kinematics and gap segment IDs.

    ``max_gap_s`` splits intervals longer than the explicitly supplied limit.
    ``movement_epsilon_mm`` masks unreliable headings only; it does not remove
    distance, turn low speeds into zero, or infer stationary behavior.
    A stationary interval has no velocity heading, so no turn is inferred
    across it. Acceleration is the signed derivative of scalar speed, not the
    full vector acceleration (which would also include centripetal motion).
    """
    missing = set(_REQUIRED).difference(df.columns)
    if missing:
        raise ValueError(f"missing required columns: {sorted(missing)}")
    if df.empty:
        raise ValueError("track table is empty")
    if df.columns.duplicated().any():
        raise ValueError("duplicate column names are not allowed")
    epsilon = _finite_setting("movement_epsilon_mm", movement_epsilon_mm)
    if max_gap_s is not None:
        max_gap_s = _finite_setting("max_gap_s", max_gap_s, positive=True)
    out = df.copy(deep=True)
    ids = out["track_id"]
    if ids.isna().any() or ids.map(lambda value: not str(value).strip()).any():
        raise ValueError("track_id must not be missing or empty")
    for column in _REQUIRED[1:]:
        try:
            out[column] = pd.to_numeric(out[column], errors="raise").astype(float)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{column} must be numeric") from exc
        if not np.isfinite(out[column]).all():
            raise ValueError(f"{column} must contain only finite values")
    out = out.sort_values(["track_id", "time_s"], kind="stable").reset_index(drop=True)
    groups = out.groupby("track_id", sort=False)
    dt = groups["time_s"].diff()
    if (dt.dropna() <= 0).any():
        raise ValueError("timestamps must increase strictly within each track")
    boundary = dt.isna()
    if max_gap_s is not None:
        boundary = boundary | (dt > max_gap_s)
    out["segment_id"] = boundary.groupby(out["track_id"], sort=False).cumsum().astype(int) - 1
    out["dt_s"] = dt.mask(boundary)
    out["dx_mm"] = groups["x_mm"].diff().mask(boundary)
    out["dy_mm"] = groups["y_mm"].diff().mask(boundary)
    out["distance_mm"] = np.hypot(out["dx_mm"], out["dy_mm"])
    out["speed_mm_s"] = out["distance_mm"] / out["dt_s"]
    out["interval_midpoint_s"] = out["time_s"] - out["dt_s"] / 2
    heading = np.arctan2(out["dy_mm"], out["dx_mm"])
    out["heading_rad"] = heading.where(out["distance_mm"] > epsilon)
    segments = out.groupby(["track_id", "segment_id"], sort=False)
    d_heading = segments["heading_rad"].diff()
    out["turn_rad"] = (d_heading + np.pi) % (2 * np.pi) - np.pi
    midpoint_dt = segments["interval_midpoint_s"].diff()
    out["turn_rate_rad_s"] = out["turn_rad"] / midpoint_dt
    out["acceleration_mm_s2"] = segments["speed_mm_s"].diff() / midpoint_dt
    numerical = out[["dx_mm", "dy_mm", "dt_s", "distance_mm", "speed_mm_s",
                     "interval_midpoint_s", "turn_rate_rad_s", "acceleration_mm_s2"]]
    if np.isinf(numerical.to_numpy()).any():
        raise ValueError("kinematics overflow; check calibration and input magnitudes")
    return out


def _median_or_none(values: pd.Series) -> float | None:
    finite = values[np.isfinite(values)]
    return float(finite.median()) if not finite.empty else None


def summarize_track(df: pd.DataFrame) -> dict[str, float | int | None]:
    """Descriptive sample summaries, not independent-animal population estimates."""
    return {
        "track_count": int(df["track_id"].nunique()),
        "observed_interval_count": int(df["speed_mm_s"].notna().sum()),
        "observed_duration_s": float(df["dt_s"].sum()),
        "observed_distance_mm": float(df["distance_mm"].sum()),
        "median_speed_mm_s": _median_or_none(df["speed_mm_s"]),
        "median_abs_turn_rate_rad_s": _median_or_none(df["turn_rate_rad_s"].abs()),
        "median_acceleration_mm_s2": _median_or_none(df["acceleration_mm_s2"]),
    }


def stop_bouts(
    df: pd.DataFrame, *, speed_threshold_mm_s: float,
    min_duration_s: float = 0.0,
) -> pd.DataFrame:
    """Find consecutive observed low-speed intervals, preserving censoring.

    A bout touching either boundary of its observed segment is censored at that
    boundary. Its duration is a lower bound, not a completed pause measurement.
    Tracking gaps are excluded rather than interpreted as time spent stopped.
    """
    threshold = _finite_setting("speed_threshold_mm_s", speed_threshold_mm_s)
    minimum = _finite_setting("min_duration_s", min_duration_s)
    required = {"track_id", "segment_id", "time_s", "dt_s", "speed_mm_s"}
    if not required.issubset(df.columns):
        raise ValueError("stop_bouts requires a normalized track table")
    records = []
    for (track_id, segment_id), segment in df.groupby(["track_id", "segment_id"], sort=False):
        steps = segment.loc[segment["speed_mm_s"].notna()].sort_values("time_s")
        stopped = (steps["speed_mm_s"].to_numpy() <= threshold)
        edges = np.diff(np.r_[False, stopped, False].astype(int))
        for start, end in zip(np.flatnonzero(edges == 1), np.flatnonzero(edges == -1)):
            selected = steps.iloc[start:end]
            duration = float(selected["dt_s"].sum())
            if duration < minimum:
                continue
            records.append({
                "track_id": track_id, "segment_id": int(segment_id),
                "start_time_s": float(selected["time_s"].iloc[0] - selected["dt_s"].iloc[0]),
                "end_time_s": float(selected["time_s"].iloc[-1]),
                "duration_s": duration, "interval_count": int(end - start),
                "left_censored": bool(start == 0), "right_censored": bool(end == len(steps)),
            })
    return pd.DataFrame.from_records(records, columns=_STOP_COLUMNS)
