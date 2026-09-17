"""Empirical summaries; fitting a biological distribution is a separate step."""
from collections.abc import Iterable
import numpy as np


def quantile_summary(values: Iterable[float]) -> dict[str, float | int]:
    """Summarize finite observations using linear percentiles and sample SD.

    Count excluded nonfinite observations explicitly. A single sample has zero
    observed dispersion, not evidence that the population variance is zero.
    """
    raw = np.asarray(list(values), dtype=float)
    if raw.ndim != 1:
        raise ValueError("distribution must be one-dimensional")
    arr = raw[np.isfinite(raw)]
    if not arr.size:
        raise ValueError("distribution contains no finite values")
    p05, p25, median, p75, p95 = np.percentile(arr, [5, 25, 50, 75, 95], method="linear")
    return {
        "count": int(arr.size), "excluded_count": int(raw.size - arr.size),
        "mean": float(arr.mean()),
        "sd": float(arr.std(ddof=1)) if arr.size > 1 else 0.0,
        "min": float(arr.min()), "p05": float(p05), "p25": float(p25),
        "median": float(median), "p75": float(p75), "p95": float(p95),
        "max": float(arr.max()),
    }
