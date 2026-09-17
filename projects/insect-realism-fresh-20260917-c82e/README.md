# Insect realism research - fresh implementation

**Status: work in progress, not the completed research pack.**

This project starts from scratch from the two user-supplied 2026-08-25 plans. It does not import the older insect implementation, its source catalog, or its claimed review counts. The desktop overlay application is a separate phase.

Repository branch: `project/insect-realism-fresh-20260917-c82e`.
Project boundary: `projects/insect-realism-fresh-20260917-c82e/`.

## Setup and verification

From this directory, with Python 3.12 or newer:

```sh
python -m venv .venv
# Activate the environment using your shell's normal activation command.
python -m pip install -e '.[test]'
python -m pytest -q
python scripts/init_pack.py
```

NumPy 2.x, pandas 2.2.x and pytest 8.x are the plan's dependency ranges. Installation into an isolated environment is recommended. The package uses a `src/` layout; install it before running scripts, or set `PYTHONPATH=src` for a source checkout.

`INSECT_REALISM_MEGA_PACK` is relative to the current working directory. Pass an explicit `--root` when initializing or ingesting elsewhere. No data is written into an installed package directory.

## Implemented foundation

The current implementation provides the canonical archive layout; strict source/evidence schemas; duplicate-aware atomic source intake; explicit source-license decisions and manifests; and streaming checksums, deterministic download manifests, and guarded resumable HTTP(S) retrieval. Source URLs and DOI variants are normalized for duplicate detection. A reference-only source never grants permission to copy its original bytes.

These are infrastructure tests, not evidence that the research corpus is complete. Consult `PLAN_EXECUTION.md` for exact progress and environment verification. Empty directories are recreated by the initializer because Git does not preserve them.

## Source intake

```sh
python scripts/add_source.py --help
```

The CLI requires a verified or explicitly uncertain license name and one of the four plan classifications. Catalog entries alone do not establish review depth. Add the matching license row, an actual evidence note, and any relevant download/asset records in the same research work unit. Never invent data or promote a restricted source merely to fill the archive.

Final acceptance requires at least 2,000 genuinely reviewed page-equivalents, all required biological and platform coverage, populated calibrated derived data, and a passing whole-corpus audit. No final release archive has been produced at this checkpoint.
