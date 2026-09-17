import math
import pytest
from insect_research.distributions import quantile_summary


def test_quantiles_have_known_values_and_sample_sd():
    out = quantile_summary([1, 2, 3, 4, 5])
    assert out["count"] == 5
    assert out["median"] == 3
    assert out["mean"] == 3
    assert out["sd"] == pytest.approx(math.sqrt(2.5))
    assert out["p05"] == pytest.approx(1.2)
    assert out["p95"] == pytest.approx(4.8)
    assert out["min"] == 1 and out["max"] == 5


def test_nonfinite_values_are_excluded_and_count_is_reported():
    out = quantile_summary(iter([1, float("nan"), float("inf"), 3]))
    assert out["count"] == 2
    assert out["excluded_count"] == 2
    assert out["median"] == 2


def test_single_sample_has_no_observed_dispersion():
    assert quantile_summary([7])["sd"] == 0


@pytest.mark.parametrize("values", [[], [float("nan"), float("inf")]])
def test_no_finite_observations_is_an_error(values):
    with pytest.raises(ValueError, match="finite"):
        quantile_summary(values)
