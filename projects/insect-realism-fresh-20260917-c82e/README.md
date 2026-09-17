# Insect realism research - fresh implementation

**Status: source checkpoint, not the completed research Mega Pack.**

This project starts from scratch from the two user-supplied 2026-08-25 plans. No older insect implementation, catalog, derived measurement or review count was imported. The desktop overlay application is a separate phase.

Branch: `project/insect-realism-fresh-20260917-c82e`.
Project boundary: `projects/insect-realism-fresh-20260917-c82e/`.

## Setup and verification

From this directory with Python 3.12 or newer:

```sh
python -m venv .venv
# Activate the environment using your shell's normal activation command.
python -m pip install -e '.[test]'
python -m pytest -q
python scripts/init_pack.py
```

Dependency contract: NumPy >=2,<3; pandas >=2.2,<3; pytest >=8,<9. Install into an isolated environment. The package uses a src layout: install it before running scripts, or set `PYTHONPATH=src` in a source checkout.

`INSECT_REALISM_MEGA_PACK` is relative to the current working directory. Pass an explicit `--root` when initializing or ingesting elsewhere. Installed package directories are never used as a writable data store. Git does not preserve empty directories, so run the initializer after checkout.

## Current milestone

Tasks 1-5 are implemented: canonical archive paths, strict source/evidence schemas, duplicate-aware atomic catalog intake, explicit licence decisions, validated manifests, streaming checksums and guarded resumable HTTP(S) retrieval.

Task 6 has started with five cataloged sources and five provisional evidence notes. **Accepted reviewed page-equivalents: 0 / 2,000.** Partial text, metadata, abstract-only assessments and unresolved figure/data checks have deliberately not been credited as completed reviews. No real trajectory dataset has been downloaded or normalized.

These infrastructure tests do not prove the biological corpus is complete. Tasks 7-16 remain outstanding. There is no whole-corpus acceptance auditor or final release builder yet. Do not rename the source checkpoint ZIP to the final Mega Pack ZIP.

## Verified environments

At foundation commit `21bba13210f8a31ea3f4f2ce3d3b52308cbd5166`, GitHub Actions ran 137 project tests successfully with Python 3.12.14 and pytest 8.4.2. It also passed all 35 shared-workspace tests and `agentctl.py validate`. See `VERIFICATION.md` for the run, versions and limitations.

Local testing additionally passed on Python 3.13.5; the preinstalled local pytest 9.0.2 is outside the declared test-extra contract, which was separately verified by CI rather than silently loosened.

## Source intake and continuation

```sh
python scripts/add_source.py --help
```

The CLI requires an explicit source identity and licence decision. Add the matching licence row, evidence note and review-status record in the same work unit. Cataloging a paper never automatically creates review credit. Source notes are provisional observations, not approved application defaults.

See `PLAN_EXECUTION.md`, `research/NEXT_ACTION.md`, and `00_MASTER_INDEX/PRIORITY_SOURCES.md` inside the staging tree. The downloadable source checkpoint preserves both original uploaded plans unchanged. The remote branch records their exact hashes in `docs/superpowers/INPUTS.md`; the two full input copies are checkpoint-only files.
