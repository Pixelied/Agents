# Insect desktop application - continued execution

Approved plan: `docs/APPROVED_PLAN.md`
Approved specification: `docs/APPROVED_DESIGN.md`
Project: `projects/insect-realism-desktop-app`
Branch: `feat/insect-continuation-03-q7b4`
Continuation base: `a8938d4245ab43a0f48019c5a6d179693353c5ba`
Worker/task: `astra-insect-resume-m5r2` / `insect-continuation-03-q7b4`

## Inherited state - not restarted

The authenticated GitHub Actions source bundle for run `35416400017` restored
the existing branch and its history. Existing simulation, renderer, native
adapters, settings, packaging, and earlier fixes are preserved. The supplied
Continuation 02 ZIP is historical evidence, not the execution base.

## Task 3 / Tasks 19-20 - portable profile compilation

Status: portable compiler correction complete at `7074a927bfb6cfb19d27688157e0599d0baa1f74`.

The latest native run passed formatting, strict Clippy, Python tooling tests,
input-probe unit tests, core release tests, controller release tests, and profile
compilation/validation on both platforms. Exact profile reproduction failed.
Decoded native outputs differ from the former Linux bundle in 25 near-zero
acceleration samples per platform, at most 1.33094e-15 mm/s^2.

Ruling: replace only build-time host transcendental functions with pinned
`libm 0.2.16` software math, and version the compiled output as donor-transfer.2.
The input snapshot, measurements, sources, units, and provenance are unchanged.
No tolerance-based acceptance replaces the exact-byte release gate. Cost if
wrong: a numerically different profile; bit-exact regression vectors and native
recompilation are required before accepting it.

RED: three portability regressions fail on the inherited compiler (exit 101).
Two expose actual sample-bit differences; the third pins the complete new bundle.

GREEN: all three numerical portability regressions and all 12 profile-compiler
integration tests pass in release mode. The complete new bundle SHA-256 is
`ce558669b3b17d5e85282c1bc94ea8c0e34b6d86311ded3a2acc2d31552c502b`.

The local old/new comparison found 30 changed derived values: 17 acceleration
and 13 turn samples. All but one differ only at near-zero floating precision.
At `H36T001R013:leader`, original frame 8510, the turn changes from
-9.415352821350098 to +9.415352821350098 rad/s: the two values encode opposite
sign conventions for an approximately 180-degree sampled reversal. The input
coordinates cannot identify the direction traversed between those observations.
This is explicitly retained as a versioned numerical interpretation, not described
as a byte-identical or perfectly behavior-identical reproduction of version .1.

Ruling: use the pinned software kernel's half-turn sign rather than retain a
host-dependent tie. Cost if wrong: the direction of this one interpolated donor
turn differs; all original coordinate observations and provenance remain intact.

## Remaining release qualification

Both native jobs in run `35418053674` passed on the portable correction.
The original Windows artifact was subsequently superseded: import inspection
found an external VCRUNTIME140.dll dependency. The packaging fix is
`f3415f05d3bffcfb74be696938f59b112431f8c8`; its run is `35419485077`.
Current package outcomes and final artifact hashes are recorded in
`docs/CONTINUATION_STATUS.md`, not inferred from the earlier green build. Native interactive input, physical-size calibration, and representative
hardware performance remain separate acceptance gates. A fresh 600.006-second
offscreen lifecycle soak on the final application code passed its independent audit;
the required two-hour wall-clock endurance run remains open and is not inferred.


## Task 19 - native runtime packaging correction

Commit: `f3415f05d3bffcfb74be696938f59b112431f8c8`.
Seven regressions failed before the fix and pass after it. The old actual Windows
executable is also rejected, demonstrating the check is not merely a fixture test.
The package inspector reads normal and delayed PE import tables, validates RVA
mapping and termination, and rejects external Visual C++ runtime dependencies.
The MSVC build applies static CRT selection to all target dependencies and restores
its environment. This is not proof of all possible dynamically loaded libraries.
Application Rust source and the .2 profile are unchanged by this packaging fix.

## Task coverage and final boundary

| Tasks | Current implementation/evidence | Remaining boundary |
|---|---|---|
| 1-2 | Existing coordinated project and source history preserved; workspace tests/validator pass | No new independent project or reset |
| 3 | Exact cross-host profile .2 reproduction; raw-pack compiler tests and provenance | Donor transfer remains explicitly qualified |
| 4-5 | Settings, presets, migration, physical-unit/topology tests | Real-monitor calibration and usability |
| 6-8 | Fixed-step biology, traits, trails, spatial/encounter/locomotion regressions | Biological donor-model limits documented |
| 9-11 | Real offscreen GPU tests and deterministic visual readbacks | Calibrated 1:1/high-refresh human review |
| 12-16 | Native adapters, controller, recovery, input-probe tests | Real input, focus, hot-plug, sleep/wake and native compositor |
| 17 | Five candidates independently accounted for; ants-only gate | No weak secondary species shipped |
| 18 | Four audited workloads, zero warmed simulation allocations | Representative native 60 FPS and full endurance |
| 19 | Native packaging, dependency audit, read-only branch CI, draft-only tag workflow | Native install/uninstall and distribution trust as applicable |
| 20 | Docs/spec mapping, final test evidence, source/package checksums | Manual release gates remain open |

Design sections 1-16 are mapped individually in `docs/ACCEPTANCE_MATRIX.md`.
Exact command logs, counts, source revisions, benchmark/soak limits and native
artifact results are indexed in `docs/CONTINUATION_STATUS.md` and the delivered
evidence bundle. No independent subagent was available; review was an executed
self-review, not represented as independent approval.
