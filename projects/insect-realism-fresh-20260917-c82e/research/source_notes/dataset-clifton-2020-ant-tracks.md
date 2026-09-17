# Uneven substrates constrain walking speed in ants through modulation of stride frequency more than stride length

- Source ID: dataset-clifton-2020-ant-tracks
- Stable identifier: 10.6075/J0RR1WM2
- Canonical source: https://datadryad.org/dataset/doi:10.6075/J0RR1WM2
- Source kind: dataset
- Retrieved: 2026-09-17
- License: CC0 repository policy; individual payload review pending
- License class: REFERENCE_ONLY
- Reviewed page-equivalent: 0

## Why this source matters

Explicit calibration and column semantics support future normalization.

## Measurements / observations extracted

Calibration is 31.992 pixels/mm at 240 frames/s. Processed x/y omit stopped or slow movement. x_raw/y_raw are already filtered; x_kal/y_kal retain earlier centroid output.

## Units and uncertainty

Pair each coordinate representation with its documented frame index; do not assume retained rows are consecutive frames.

## What can enter the derived database

Calibration and censoring metadata only; no measured speed or pause distribution has been computed.

## What must not be redistributed

Payload inclusion remains withheld pending file-level inspection.

## Conflicts / limitations

Processed x/y would bias pause statistics. Pickle decoding requires a separate safety review.

## Follow-up sources cited by this work

Companion study: paper-clifton-2020-uneven.

Review status: provisional. Scope, access limits and withheld page credit are recorded in `00_MASTER_INDEX/SOURCE_REVIEW_STATUS.csv`.
