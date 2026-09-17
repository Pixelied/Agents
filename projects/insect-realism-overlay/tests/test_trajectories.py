import math

import numpy as np
import pandas as pd
import pytest
from pandas.testing import assert_frame_equal

from insect_research.trajectories import normalize_track, stop_bouts, summarize_track


def track(times=(0, 0.5, 1), xs=(0, 5, 10), ys=None, name="a"):
    return pd.DataFrame({"track_id": [name] * len(times), "time_s": times,
                         "x_mm": xs, "y_mm": ys if ys is not None else [0] * len(times)})


def test_straight_track_has_known_speed_and_zero_turn_rate():
    out = normalize_track(track())
    summary = summarize_track(out)
    assert summary["median_speed_mm_s"] == pytest.approx(10)
    assert summary["median_abs_turn_rate_rad_s"] == pytest.approx(0)
    assert out["distance_mm"].sum() == pytest.approx(10)


def test_normalization_sorts_without_mutating_or_losing_metadata():
    raw = track().iloc[::-1].assign(species="synthetic", state="test")
    original = raw.copy(deep=True)
    out = normalize_track(raw)
    assert_frame_equal(raw, original)
    assert list(out.time_s) == [0, 0.5, 1]
    assert list(out.species) == ["synthetic"] * 3


def test_tracks_are_never_connected():
    a = track(times=(0, 1), xs=(0, 2))
    b = track(times=(0, 1), xs=(100, 104), name="b")
    out = normalize_track(pd.concat([b, a], ignore_index=True))
    assert sorted(out.speed_mm_s.dropna()) == [2, 4]
    assert out.turn_rate_rad_s.isna().all()
    assert out.acceleration_mm_s2.isna().all()


def test_irregular_intervals_use_interval_midpoint_derivative():
    out = normalize_track(track(times=(0, 2, 6), xs=(0, 2, 2), ys=(0, 0, 8)))
    assert out.speed_mm_s.iloc[-1] == pytest.approx(2)
    assert out.acceleration_mm_s2.iloc[-1] == pytest.approx(1 / 3)
    assert out.turn_rate_rad_s.iloc[-1] == pytest.approx((math.pi / 2) / 3)


def test_turns_wrap_at_pi_boundary():
    angle = math.radians(179)
    out = normalize_track(track(times=(0, 1, 2), xs=(0, math.cos(angle), 2 * math.cos(angle)),
                                ys=(0, math.sin(angle), 0)))
    assert out.turn_rad.iloc[-1] == pytest.approx(math.radians(2))


def test_stops_do_not_manufacture_east_facing_headings_or_turns():
    out = normalize_track(track(times=(0, 1, 2, 3), xs=(0, 0, 0, 0), ys=(0, 1, 1, 2)))
    assert math.isnan(out.heading_rad.iloc[2])
    assert out.turn_rate_rad_s.isna().all()
    assert out.speed_mm_s.iloc[2] == 0


def test_gap_boundary_is_not_counted_as_movement_or_stop():
    out = normalize_track(track(times=(0, 1, 20, 21), xs=(0, 1, 100, 100)), max_gap_s=2)
    assert list(out.segment_id) == [0, 0, 1, 1]
    assert math.isnan(out.speed_mm_s.iloc[2])
    assert math.isnan(out.acceleration_mm_s2.iloc[3])
    assert out.distance_mm.sum() == pytest.approx(1)
    bouts = stop_bouts(out, speed_threshold_mm_s=0)
    assert list(bouts.duration_s) == [1]
    assert bool(bouts.left_censored.iloc[0]) and bool(bouts.right_censored.iloc[0])


def test_movement_epsilon_only_masks_heading_not_distance_or_speed():
    out = normalize_track(track(times=(0, 1, 2), xs=(0, 0.01, 0.03)), movement_epsilon_mm=0.02)
    assert out.speed_mm_s.iloc[1] == pytest.approx(0.01)
    assert math.isnan(out.heading_rad.iloc[1])


@pytest.mark.parametrize("column,value", [("time_s", np.nan), ("time_s", np.inf),
    ("x_mm", np.nan), ("y_mm", -np.inf), ("track_id", None), ("track_id", " ")])
def test_invalid_input_fails_instead_of_silently_dropping_rows(column, value):
    raw = track().astype({column: object})
    raw.loc[1, column] = value
    with pytest.raises(ValueError):
        normalize_track(raw)


def test_duplicate_time_is_rejected_within_track():
    with pytest.raises(ValueError, match="strictly"):
        normalize_track(track(times=(0, 1, 1)))


def test_pixel_coordinates_are_never_silently_treated_as_millimeters():
    with pytest.raises(ValueError, match="missing"):
        normalize_track(track().rename(columns={"x_mm": "x_px"}))


@pytest.mark.parametrize("kwargs", [{"max_gap_s": 0}, {"max_gap_s": np.inf},
    {"max_gap_s": -1}, {"movement_epsilon_mm": -1}, {"movement_epsilon_mm": np.nan}])
def test_invalid_analysis_configuration_is_rejected(kwargs):
    with pytest.raises(ValueError):
        normalize_track(track(), **kwargs)


def test_empty_input_is_rejected():
    with pytest.raises(ValueError, match="empty"):
        normalize_track(track().iloc[:0])


def test_unobservable_metrics_are_none_not_invented_zero():
    summary = summarize_track(normalize_track(track(times=(0,), xs=(0,))))
    assert summary["median_speed_mm_s"] is None
    assert summary["median_abs_turn_rate_rad_s"] is None


def test_stop_duration_is_elapsed_time_not_number_of_frames():
    out = normalize_track(track(times=(0, 1, 2, 4, 5), xs=(0, 1, 1, 1, 2)))
    bouts = stop_bouts(out, speed_threshold_mm_s=0.1)
    assert len(bouts) == 1
    row = bouts.iloc[0]
    assert row.start_time_s == 1
    assert row.end_time_s == 4
    assert row.duration_s == 3
    assert not bool(row.left_censored) and not bool(row.right_censored)


def test_censored_stops_are_labeled_not_presented_as_complete():
    out = normalize_track(track(times=(0, 1, 2, 3, 4), xs=(0, 0, 1, 1, 1)))
    bouts = stop_bouts(out, speed_threshold_mm_s=0)
    assert list(bouts.duration_s) == [1, 2]
    assert list(bouts.left_censored) == [True, False]
    assert list(bouts.right_censored) == [False, True]
    assert len(stop_bouts(out, speed_threshold_mm_s=0, min_duration_s=1.5)) == 1


def test_no_stop_returns_typed_empty_table():
    bouts = stop_bouts(normalize_track(track()), speed_threshold_mm_s=0)
    assert bouts.empty
    assert {"track_id", "duration_s", "left_censored", "right_censored"} <= set(bouts.columns)


@pytest.mark.parametrize("threshold", [-1, np.nan, np.inf])
def test_invalid_stop_threshold_is_rejected(threshold):
    with pytest.raises(ValueError):
        stop_bouts(normalize_track(track()), speed_threshold_mm_s=threshold)


def test_overflowing_kinematics_is_an_error_not_a_valid_measurement():
    with pytest.raises(ValueError, match='overflow'):
        normalize_track(track(times=(0, 1, 2), xs=(-1e308, 1e308, 1e308)))
