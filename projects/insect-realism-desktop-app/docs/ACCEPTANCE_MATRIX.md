# Approved specification acceptance map

Authority: `APPROVED_DESIGN.md`; task sequence: `APPROVED_PLAN.md`.
Exact current commands/results are in `CONTINUATION_STATUS.md` and their logs.
This map describes coverage, not automatic sign-off of unchecked behavior.

| Specification section | Implementation/evidence | Remaining qualification |
|---|---|---|
| 1. Purpose | Native utility, procedural physically scaled ants | Human realism review at calibrated 1:1 |
| 2. Locked decisions | Rust, wgpu, presets, two native targets, no injection | Verify actual desktop interaction |
| 3. Execution base | Existing continuation branch, preserved history, scoped leases | No restart or unrelated-tree merge |
| 4. Architecture | Separate profile/display/settings/simulation/render/platform/app crates | Native integration acceptance |
| 5. Simulation | Fixed clock, traits, motion tracks, spatial hash, trails, edge/spawn logic | Validate donor transfers against target behavior |
| 6. Rendering | Instanced 2.5D, articulated limbs, coverage tests, LOD and generated fixtures | Physical-size, compositor alpha and high-refresh review |
| 7. Overlay behavior | Fail-closed gate, native adapters, panic, lifecycle/recovery tests | Real clicks/drag/scroll/keys, focus, hot-plug, sleep/wake |
| 8. Settings/UX | Presets, atomic configuration, calibration and separate GUI tests | Native first-run/usability and corrupted-config review |
| 9. Performance | Instrumented four-scenario benchmark and independent sample audits | Representative native 1,000+ / 60-FPS qualification |
| 10. Testing | Unit/property/GPU/profile tests; actual input-probe harness | Native manual matrix and two-hour endurance |
| 11. Packaging | Native .app/DMG and MSI/portable recipes, payload inspection | Actual install/uninstall; distribution trust as applicable |
| 12. CI/versioning | Native branch pipeline; tag/manual draft-release pipeline | Inspect tag path when deliberately invoked |
| 13. Documentation | Architecture, biology, assumptions, platform, performance, release/testing docs | Update acceptance record as real hardware results arrive |
| 14. Execution contract | Existing work continued; failures investigated; exact evidence retained | Do not call unchecked gates complete |
| 15. Definition of done | See all evidence above | Not fully satisfied while native manual gates remain open |
| 16. Non-goals | No cloud account, screen capture, injection, game semantics, mandatory updater | Preserve these boundaries |

Secondary creatures remain ants-only by the evidence/reuse gate; this is a
permitted design outcome, not a placeholder species implementation.

Native code compilation and synthetic event tests do not establish actual input
pass-through. Development installers are useful test artifacts, not certification
that the utility is safe above critical work or protected surfaces.
