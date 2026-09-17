# Fresh execution record

## Scope and origin

- Requested: 2026-09-17, explicitly start from scratch.
- Branch: `project/insect-realism-fresh-20260917-c82e`.
- Clean main implementation base: `243c2dbf1100aa0a706cee5de90c9cb73c1a011e`.
- Coordination-only registration commit / remote branch starting point: `7fe66ac60feea723eb73fd8ec024d0ea26797176`.
- No previous insect project code, catalogs, measurements, or review counts were imported. Original uploaded plans are preserved unchanged in `docs/superpowers/` in the downloadable source checkpoint. Their exact input hashes are recorded in `docs/superpowers/INPUTS.md` on the remote branch; the two full input copies are checkpoint-only files.
- Local development uses an isolated newly initialized working repository containing only this project; local commit history is not represented as cloned main ancestry. Remote project changes preserve the clean-main parent through the GitHub tree/commit API.

## Task status

| Task | Status | Evidence |
| --- | --- | --- |
| 1. Workspace | Implemented and locally tested | 13 exact directories, idempotence, path and symlink guards, initializer CLI |
| 2. Schemas | Implemented and locally tested | Required metadata, dates/enums, finite measurements, safe provenance, exact CSV fields |
| 3. Catalog | Implemented and locally tested | Atomic writes, lock contention, DOI/URL aliases, stable ordering, intake CLI |
| 4. License policy | Implemented and locally tested | Four exact decisions, manifest consistency and canonical booleans |
| 5. Downloads/manifests | Implemented and locally tested | Streaming hashes; strict deterministic JSON; real local HTTP interruption/Range/hash tests |
| 6. Primary evidence collection | Not started at foundation checkpoint | 0 cataloged sources; 0 reviewed page-equivalents |
| 7-13. Derived data and specialist research | Not started | No invented measurements or placeholder completion claims |
| 14. Whole-corpus audit | Not implemented at foundation checkpoint | Final acceptance is not available |
| 15. Evidence-based runtime specifications | Not started | Requires reviewed evidence and successful audit |
| 16. Final release packaging | Not started | No final Mega Pack ZIP |

## Test-first execution

Each task's tests were written and executed before its implementation. The initial runs failed because the planned modules did not yet exist. After implementation, cumulative full-suite results were: task 1: 14 passed; task 2: 55 passed; task 3: 70 passed; task 4: 89 passed; task 5: 137 passed.

Local verification: Python 3.13.5, NumPy 2.3.5, pandas 2.2.3, pytest 9.0.2, warnings treated as errors. **The preinstalled local pytest version is outside the plan's pytest >=8,<9 contract.** The dependency declaration has not been weakened; the project CI requests Python 3.12 and installs the declared test extra. CI results must be checked separately before claiming that environment passes.

The download tests use a real loopback HTTP server, not external internet. They cover 200/206 responses, interruption, restart when Range is ignored, changed ETag and bad Content-Range rejection, incomplete bodies, cache validation, corrupt-data rejection, symlink paths, locks, and unchanged destination on errors. They do not prove that every external host supports resumption.

## Deliberate corrections to illustrative plan snippets

1. The sample reviewed-page test double-counted the same source record. This conflicts with the plan's explicit no-padding rule. The counter now rejects duplicates; it does not report duplicate mirrors as extra research.
2. Archive paths are working-directory relative or explicitly supplied, not rooted in a potentially read-only installed package.
3. Validation rejects invalid dates/types, non-finite numbers, malformed digests, contradictory license flags, unsafe relative paths, and duplicate source aliases rather than checking only for non-empty strings.
4. Download resumption verifies source binding, response offsets, entity validators and expected hashes. It does not blindly append every 206 response.
5. Metadata changes are atomic and use cooperative locks. A failed write preserves the previous file. Locks are not silently stolen.
6. Tests deliberately allow signed acceleration; generic rejection of every negative physical value would incorrectly reject deceleration.

These strengthen the approved goal without asserting that infrastructure satisfies the research gate.
