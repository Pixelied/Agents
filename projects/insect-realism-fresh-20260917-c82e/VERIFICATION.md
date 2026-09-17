# Verification evidence

## GitHub-hosted verification of the foundation

- Commit: `21bba13210f8a31ea3f4f2ce3d3b52308cbd5166`
- Branch: `project/insect-realism-fresh-20260917-c82e`
- Run: https://github.com/Pixelied/Agents/actions/runs/35230696595
- Job: `105233720924`
- Completed: 2026-09-17T14:00:45Z
- Result: success. The actual job steps and full log were retrieved, not inferred from an empty workflow listing.

| Check | Actual result |
| --- | --- |
| CPython | 3.12.14 |
| pytest | 8.4.2 |
| NumPy | 2.5.3 |
| pandas | 2.3.3 |
| Shared-workspace unittest suite | 35 tests, OK |
| `python agentctl.py validate` | `errors: []`, `ok: true` |
| `pip install -e '.[test]'` | Editable wheel built and installed |
| Project suite | 137 passed in 5.48 seconds |
| Installed-package initializer | Succeeded; canonical archive root created |

The workflow emitted Node runtime deprecation warnings for its checkout/setup actions. This is a passing run, not a claim of warning-free CI or verified future action compatibility.

## Local execution

The implementation was built test-first in an isolated working directory. Each task's initial test invocation failed before its module existed, and its subsequent complete-suite invocation passed. Cumulative passing counts: 14, 55, 70, 89, 137. Local runtime: Python 3.13.5, NumPy 2.3.5, pandas 2.2.3, pytest 9.0.2. The local pytest version does not meet the declared >=8,<9 range; the separate GitHub run above does.

The source checkpoint includes the red/green logs under `verification_logs/`. These are execution records, not scientific-source review evidence. No external download succeeded during the initial biological collection, and no download/normalization milestone is represented as complete.

## Limits

The real HTTP regression tests use a loopback server. They do not establish successful access to every publisher or scientific dataset. Test count is not biological validation. No full corpus auditor, finalized derived database, full repository-reuse review, platform dossier, or final Mega Pack release has been verified at this checkpoint.
