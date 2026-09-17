# Insect research execution checkpoint - 2026-09-17

**Status: partial execution, not an implementation-ready research pack.**

Remote branch: `feat/insect-mega-pack-execution-20260917`

Base: `Pixelied/Agents` canonical `project/insect-realism-overlay`, commit
`7c4c0825f069ceea75d8a0764e9598983f3e60a1`.

The new branch preserves the existing project tree. No existing implementation
branch was overwritten. The shared `main` branch received coordination records
only; implementation changes are confined to `projects/insect-realism-overlay`.

## Completed in this checkpoint

Task 8 tooling: calibrated per-track kinematics, empirical distribution summaries,
stop-bout extraction, and a command-line analysis/export workflow. Tests cover
straight paths, wrapped angles, distinct tracks, irregular timestamps, missing
values, tracking gaps, stationary headings, observation censoring, overflow,
provenance, strict JSON, and refusing to overwrite existing output directories.

The normalizer never converts pixels to millimeters by guessing. Speeds are
interval averages. Speed acceleration and turn rate use the separation between
interval midpoints, correcting the plan example's unequal-time-step problem.
Stationary intervals have no velocity heading; no turn is invented across them.
A stop touching an observation boundary is labeled censored and excluded from
completed-stop duration quantiles. Thresholds remain explicit analysis settings.

An early Task 6 safeguard now distinguishes bibliographic extent from documented
review coverage. `REVIEW_LEDGER.json` starts empty rather than retrospectively
inventing page-level evidence. It records exact pages or conservatively accounted
web/code excerpts, requires an evidence note, deduplicates overlapping pages, and
rejects ambiguous mixed counting methods for a source. It cannot prove that a
reviewer actually read a source; substantive review remains required.

## Verification actually performed

`python -m pytest -q`: **68 passed** in the locally materialized pipeline suite.
`python -m compileall -q src scripts`: passed.

Environment: Python 3.13.5, NumPy 2.3.5, pandas 2.2.3, pytest 9.0.2.
The requested pytest 8 environment and Python 3.12 were not available here; those
compatibility runs remain outstanding. No dependency constraints were relaxed.

The local suite includes 13 inherited core tests plus the new tests. Direct Git
cloning and external archive downloads failed in this execution environment.
Inherited core files were materialized from matching plan code only when their
Git blob hashes matched the exact remote baseline. The existing remote
`test_display_scale.py` and `test_research_corpus.py` were inspected but were not
part of the local run because the full inherited research tree was not downloaded.
**This is not a claim that all remote project or shared workspace tests passed.**
No independent reviewer agent was available. No GitHub Actions run was performed.

## Evidence gate reopened

The inherited handoff reports 2,343 reviewed page-equivalents. The catalog includes
whole-book counts, while the reviewed evidence note for *The Ants* lacks a
page-level review record and lists 1994-2018 works under a heading saying they
were cited by a 1990 book. That chronology cannot be correct. The later works can
be researcher-suggested follow-ups, but not citations made by that book.

The existing notes and catalog are retained, not silently rewritten. Their
reported totals are **unverified claims**, not a newly certified depth result.
This checkpoint documents zero page-equivalents in the new review ledger; it does
not establish that no prior reading occurred. The plan requires actual reviewed
primary-source depth, not simply adding up source lengths.

Run from the project directory:

```sh
python scripts/verify_review_depth.py INSECT_REALISM_MEGA_PACK
```

With the current empty ledger, this must exit 1. Passing this limited safeguard
would still not replace the complete Task 14 corpus/license/coverage auditor.

## Analysis command

First prepare genuinely calibrated columns `track_id,time_s,x_mm,y_mm`.
Use an output directory that does not already exist:

```sh
python scripts/analyze_trajectory.py \
  --input /path/to/calibrated_tracks.csv \
  --source-id CATALOG_SOURCE_ID \
  --species-id SPECIES_ID \
  --behavior-state OBSERVED_STATE \
  --calibration-notes 'Document the exact spatial and temporal calibration source' \
  --stop-threshold-mm-s EXPLICIT_REVIEWED_ANALYSIS_THRESHOLD \
  --output-dir /path/to/new_analysis_directory
```

Replace the uppercase values with reviewed inputs. No biological threshold is
provided by this example. Outputs include normalized tracks, stop bouts, strict
JSON with the input SHA-256 and analysis settings, and four summary CSV tables.
Outputs remain analysis-only until source, calibration, and licensing review.
Pooled sample quantiles are not time-weighted or independent-animal estimates.

## Plan status and next actions

| Tasks | Verified status |
| --- | --- |
| 1-5 | Inherited core pipeline preserved; 13 inherited local core tests passed. |
| 6 | Reopened at evidence review gate. Catalog claims require page/section-level validation. |
| 7 | Existing anatomy/scale work preserved; its complete data-dependent tests were not rerun. |
| 8 | Tooling and edge-case tests implemented. Real calibrated dataset normalization remains blocked by file retrieval. |
| 9-13 | Not completed in this checkpoint. Do not infer completed repository, overlay, asset, or secondary-creature audits. |
| 14 | Early review-depth safeguard only. Full corpus auditor remains outstanding. |
| 15-16 | Not completed. No implementation-ready specs or final Mega Pack ZIP certified. |

Next: reconcile inherited review notes and chronology; record actual reviewed
pages/sections; retrieve a licensed measured dataset and its calibration; run the
new analyzer on it; then populate evidence-backed biology tables and proceed
through the remaining review gates. See `research/checkpoints/2026-09-17-source-access.md`.
