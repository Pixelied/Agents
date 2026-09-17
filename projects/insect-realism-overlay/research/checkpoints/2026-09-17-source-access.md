# Primary-source access and normalization checkpoint

Retrieved/rechecked: 2026-09-17. These are access and metadata observations, not
claims of completed paper review or normalized biological measurements. No raw
external dataset bytes were added by this checkpoint.

## Argentine-ant locomotion candidate

Clifton, Holway and Gravish (2020), *Uneven substrates constrain walking speed in
ants through modulation of stride frequency more than stride length*.

Paper: https://doi.org/10.1098/rsos.192068
Dataset: https://doi.org/10.6075/J0RR1WM2
Metadata reviewed: https://datadryad.org/dataset/doi:10.6075/J0RR1WM2

The dataset's usage notes explicitly specify 31.992 pixels/mm and 240 frames/s.
They distinguish `x_kal`/`y_kal` from processed `x`/`y`; the latter remove slow or
stopped motion and observations near frame edges. Therefore processed `x`/`y`
would bias a pause-duration analysis. The `frames` and `frames_final` arrays must
be paired with their corresponding coordinates, never substituted implicitly.
The listed archive consists of pandas pickle files. Pickle can execute code:
inspect and isolate any conversion; do not add automatic unpickling to a generic
public-data download helper. The direct first-file endpoint did not return usable
bytes in this environment: https://datadryad.org/downloads/file_stream/254951

## antGait candidate

Choi et al. (2023), *antGait program file for speed estimation of ants and ant
tracking data*. DOI: https://doi.org/10.5061/dryad.g79cnp5tq
Metadata: https://datadryad.org/dataset/doi:10.5061/dryad.g79cnp5tq

The record separates genuine trajectories from simulated trajectories and
noise-added estimates, and identifies a Data Interpretation Guide inside
`Data.zip`. Species, calibration and which files are genuinely measured must be
verified from that guide before promotion. The direct archive endpoint returned
403 through web retrieval; a container download also failed:
https://datadryad.org/downloads/file_stream/2618238

Dryad's published terms describe dataset publication under CC0. That does not
remove the need to inspect actual item/component terms, distinguish data from
software, preserve scholarly attribution, and verify downloaded checksums.
Terms reviewed: https://datadryad.org/terms (updated May 20, 2025).

## Bristol alternate - research-only licensing caution

Franks, Sendova-Franks and Christensen (2014), *Universality in Ant Behaviour*.
DOI: https://doi.org/10.5523/bris.cmcs6znssfim12zo6zzmur1hq
Record: https://data.bris.ac.uk/data/dataset/cmcs6znssfim12zo6zzmur1hq
Calibration note: https://data.bris.ac.uk/datasets/cmcs6znssfim12zo6zzmur1hq/Data_Information.txt

The record describes 101 individual tracks with seconds and millimeter
coordinates. Its item-level license is the Non-Commercial Government Licence,
not an unrestricted app-shipping license. Do not treat this as a commercial
asset/source-data donor without resolving that restriction. The archive download
failed in the container, and the child directory timed out through web retrieval.

## No substitution of synthetic data

Synthetic paths in unit tests test arithmetic and error handling only. They are
not entered in species/behavior tables or counted toward real-dataset acceptance.
No source page-equivalents are credited for merely locating these records.
