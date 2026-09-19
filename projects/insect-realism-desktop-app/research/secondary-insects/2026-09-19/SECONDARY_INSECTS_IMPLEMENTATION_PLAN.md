# Secondary insects implementation plan

## Goal

Add **2–4** secondary insects without regressing the verified ant system or collapsing distinct biology into one generic wander controller.

## Architecture rule

Share infrastructure, not biology. Shared systems may include fixed-step scheduling, display-mm transforms, deterministic RNG, broad-phase spatial indexing, profile provenance, renderer resource management and batching. Each species owns its locomotion/state machine, morphology and renderer rig.

## Task 0 — coordination and baseline

- Branch from the latest verified application lineage.
- Preserve the ant runtime/profile as a regression baseline.
- Acquire the smallest implementation leases per phase.
- Run the full locked workspace/tooling baseline before source changes.

## Task 1 — machine-readable evidence gate v2

Create a gate that records for every candidate:
- species and exact life stage;
- direct vs cross-life-stage/cross-species evidence;
- raw/open dataset availability;
- morphology/trajectory/gait coverage;
- legal asset status;
- unresolved hard gates;
- allowed runtime features.

A hard-gated candidate cannot become `qualified=true`.

## Task 2 — backward-compatible profile schema extension

Keep the existing common physical/provenance contract and add type-safe species variants, e.g.:
```rust
enum SpeciesModel {
    Ant(AntModel),
    Drosophila(DrosophilaModel),
    GermanCockroachNymph(GermanCockroachModel),
    BedBug(BedBugModel),
    RedFlourBeetle(RedFlourBeetleModel),
}
```

Do not make a lowest-common-denominator bag of ant parameters.

Validation rejects:
- life-stage mismatches;
- unsupported donor transfers;
- absent gait evidence for an articulated release profile;
- screen-content host/heat/odor semantics;
- larval Tribolium gait in an adult profile.

The old ant profile should remain byte-identical unless a separate deliberate profile revision is approved.

## Task 3 — reproducible data extraction

Build source-specific extractors for:
- Pratt/Mendes Drosophila gait;
- Jeanson first-instar cockroach motion;
- Cimex host-search/dispersal summaries;
- Tribolium trajectory/obstacle datasets.

Each extractor writes normalized units, source hashes, provenance and compact test fixtures. Missing values remain missing.

## Task 4 — Drosophila simulation

Species module: `simulation/src/species/drosophila.rs`.

States:
- pause/stand;
- spontaneous walk;
- directed walk;
- surface turn;
- groom;
- pre-takeoff;
- flight;
- landing;
- edge exit.

Walking uses direct speed distributions, speed-dependent gait mixtures and turn slowdown. Flight uses a separate integrator; no teleporting and no arbitrary reuse of walking speed.

## Task 5 — Drosophila renderer

Add a species-specific procedural rig/shader:
- ~2 mm body;
- head/thorax/abdomen;
- six legs;
- folded/translucent wing state;
- takeoff/flight/landing wing state;
- speed-coupled gait.

Keep appendage anti-aliasing/coverage discipline from the ant renderer; no sprite dependency.

## Task 6 — first-instar German cockroach simulation

Species module: `simulation/src/species/german_cockroach_nymph.rs`.

Use the measured state machine:
- central exploratory motion;
- wall-follow/peripheral mode;
- short awake stop;
- long resting stop;
- leave-wall transition;
- disturbance escape;
- aggregation arrest only after direct neighbor-response evidence is extracted.

Do not use ant pheromone trails.

## Task 7 — cockroach gait + renderer

Render the ~3 x 2 mm body with ~3 mm antennae and real wall-side antennal behavior.

Fine leg-phase rendering stays gated until first-instar evidence is extracted. Adult turning mechanics are hypothesis-only.

## Task 8 — bed bug whole-body model

Species module: `simulation/src/species/bed_bug.rs`.

Realistic baseline:
- slow stop/start meander;
- frequent direction changes;
- long pauses;
- occasional longer movement bouts;
- no flight.

Host/heat conditions are validation/synthetic-state data only, not screen sensors.

## Task 9 — bed bug renderer

Use a true bed-bug silhouette:
- ~4.4–5.0 mm flattened oval body;
- broad segmented abdomen;
- small head/pronotum;
- laterally visible six legs;
- no wings.

Release stays gait-gated until direct adult footfall timing is validated.

## Task 10 — red flour beetle model

Before adult-gait qualification, allow only:
- whole-body adult trajectory model;
- measured edge affinity;
- obstacle slow/stop/reentry;
- individual activity variation;
- optional threat immobility only under an evidence-appropriate trigger.

Not allowed:
- larval gait constants;
- invented adult leg-cycle phase;
- common flight without direct probability/trigger data.

If the gait gate cannot be cleared, ship three secondary insects and leave Tribolium disabled.

## Task 11 — red flour beetle renderer

Distinct ~3–4 mm beetle rig:
- elongated reddish-brown body;
- hard elytra with central seam/ridge cues;
- clubbed antennae where resolvable;
- six legs;
- normally hidden wings.

## Task 12 — mixed-species scheduler

Maintain species-specific SoA state stores with shared broad-phase indexing.

- no O(n²) cross-species loop;
- no fake interspecies ecology;
- ant trail field remains ant-only;
- minimal geometric avoidance is allowed;
- chemical/social fields remain species-specific unless direct evidence says otherwise.

## Task 13 — renderer batching/performance

One compact batched draw path per species/LOD family, never one draw per creature.

Benchmarks:
- 1,000 total mixed creatures;
- 1,000 of each species separately;
- 2,000–5,000 mixed stability;
- zero warmed simulation hot-path allocations;
- release p99 ≤16.67 ms for the declared 1,000-creature workload on representative native hardware.

## Task 14 — species-specific cursor disturbance

No universal run-away multiplier.

- Ant: preserve current behavior.
- Fly: rapid local reorientation; takeoff only if separately qualified.
- Cockroach nymph: terrestrial escape can be supported, but tune only from direct evidence or label transfer.
- Bed bug: Realistic cursor reaction remains off until directly qualified.
- Beetle: cursor proximity is not tactile brush contact; do not auto death-feign.

Non-measured mappings are `EngineeringAssumption` and off in strict Realistic mode.

## Task 15 — settings/presets

Keep the normal UI small:
- per-species enable;
- population mix;
- no biology cockpit in normal flow.

Realistic enables only qualified species and evidence-only behavior. Light/Heavy/Nightmare change population pressure, not biologically fake speed multipliers.

## Task 16 — distribution-level biological validation

Compare simulation to measurements using full distributions:
- speed;
- stop/move bout duration;
- turn/angular velocity;
- straight-run persistence;
- net displacement;
- edge/wall occupancy;
- gait occupancy by speed;
- grooming/rest/death-feigning duration where applicable.

Record quantiles and a predeclared distribution distance/tolerance. Do not tune only to means.

## Task 17 — 1:1 physical visual validation

Render:
- fly 2.0 mm;
- cockroach nymph 3.0 mm;
- bed bug 4.5–5.0 mm;
- beetle 3.5 mm;
- mixed scene;

at 96, 110, 144, 220+ PPI, all headings, bright/dark/high-frequency backgrounds and LOD transitions.

## Task 18 — native overlay/input safety

Re-run click-through, drag/scroll/mouse/keyboard pass-through, no focus, panic hide, tray/menu, mixed DPI, hot-plug, sleep/wake and renderer recovery. Secondary species cannot weaken the overlay safety contract.

## Task 19 — endurance/performance acceptance

Repeat the final application's release methodology:
- representative native GPU;
- 1,000+ mixed live insects;
- full **two-hour wall-clock** endurance;
- resource/RSS trend review;
- no hidden draws while hidden;
- source fingerprint unchanged during verification.

## Task 20 — shipping gate

Expected:
- Drosophila: likely qualifies after implementation validation.
- German cockroach first instar: likely qualifies once fine gait presentation is validated.
- Bed bug: requires direct adult gait extraction.
- Red flour beetle: requires direct adult gait evidence.

The release may enable **2, 3, or 4** new insects. Evidence decides the count.

## Definition of done

- Ant source/profile remains regression-clean.
- Every enabled species has traceable target-stage scale + locomotion evidence.
- No adult model silently uses larval gait.
- No fake screen-content host sensing.
- No unaudited publisher/reference image is shipped.
- Physical-size rendering passes.
- Native mixed-species performance/input/endurance gates pass.
- Source/data manifests and checksums are preserved with release evidence.
