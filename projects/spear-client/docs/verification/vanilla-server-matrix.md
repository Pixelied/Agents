# Minecraft 26.1.2 Spear Client — Vanilla Server Verification Matrix

## Status

**Runtime verification status: NOT RUN.**

This matrix is the protocol for testing the client mod against an **unmodified Minecraft Java Edition 26.1.2 dedicated server**. Source-derived calculations are recorded separately from observed outcomes. A source prediction is not a PASS.

At the time this document was created, the repository did not have a successful Java 25 Loom build/runClient gate in the available execution environment, so no row below is marked PASS or FAIL.

## Required server/client conditions

- Official vanilla Minecraft Java Edition 26.1.2 dedicated server JAR.
- No Fabric Loader on the server.
- No plugins, server mods, datapacks, command blocks, or operator-only helpers used to enable an outcome.
- Attacker: Fabric 26.1.2 client with this mod.
- Target: ordinary compatible client with no required mod.
- Both sides record what they observe when target HP/death is relevant.
- Client evidence logs prove only what the attacker client attempted/observed. They do **not** prove target HP, damage, death, or server acceptance by themselves.

## Result vocabulary

- `PASS` — the row's externally observable acceptance criteria were demonstrated on the vanilla server.
- `FAIL` — the row was executed and contradicted the claimed behavior.
- `INCONCLUSIVE` — the row was not run, evidence was incomplete, tracking was lost, or the result could not be distinguished reliably.

## Evidence log format

Each completed client sequence emits one line in this shape:

```text
sequence=<id> kind=<kind> result=<done|corrected|aborted> target=<id> packets=<n> origin=<x,y,z> maxRequestedDelta=<d> predictedKnownForward=<d> predictedRawDamage=<d> predictedReach=<d> corrections=<n>
```

`predicted*` fields are source-model values. They are not server-result fields.

---

## One-Tap

### Source-model notes

The current Smart One-Tap path starts legitimate main-hand spear use, waits the actual `KineticWeapon.delayTicks()`, then uses a conservative back-to-origin movement sequence. The planner targets a raw-damage threshold of 72 and currently resolves to a 6.0-block back distance for every ordinary spear tier in the supplied 26.1.2 source.

With a stationary target and an accepted final 6.0-block forward known-movement delta, the source model gives relative speed `6 * 20 = 120` and these raw-damage predictions:

| Spear tier | Source damage multiplier | Charge delay (ticks) | Planned back distance | Predicted raw damage | Runtime status |
|---|---:|---:|---:|---:|---|
| Wooden | 0.700 | 15 | 6.0 | 85 | INCONCLUSIVE — not run |
| Stone | 0.820 | 14 | 6.0 | 99 | INCONCLUSIVE — not run |
| Copper | 0.820 | 13 | 6.0 | 99 | INCONCLUSIVE — not run |
| Iron | 0.950 | 12 | 6.0 | 115 | INCONCLUSIVE — not run |
| Golden | 0.700 | 14 | 6.0 | 85 | INCONCLUSIVE — not run |
| Diamond | 1.075 | 10 | 6.0 | 130 | INCONCLUSIVE — not run |
| Netherite | 1.200 | 8 | 6.0 | 145 | INCONCLUSIVE — not run |

Those numbers are **source-model raw damage before the target's armor, protection, resistance, absorption, blocking, hurt-invulnerability state, or other runtime conditions**.

### Target matrix

Fill `Start HP`, `Observed result`, `Corrections`, `Max request delta`, and `Ping` from the actual run. Target-side/server-observable HP/death evidence is required for PASS.

| Spear | Target armor | Start HP | Observed result HP/death | Corrections | Max request delta | Ping | Source prediction | Runtime result |
|---|---|---:|---|---:|---:|---:|---|---|
| Wooden | None | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Wooden | Full iron | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Wooden | Full diamond | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Wooden | Full netherite | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Wooden | Protection IV netherite | — | — | — | — | — | Raw model 85; source calculation suggests 72 raw is a relevant 20-HP threshold under ordinary conditions | INCONCLUSIVE — not run |
| Stone | None | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Stone | Full iron | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Stone | Full diamond | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Stone | Full netherite | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Stone | Protection IV netherite | — | — | — | — | — | Raw model 99 | INCONCLUSIVE — not run |
| Copper | None | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Copper | Full iron | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Copper | Full diamond | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Copper | Full netherite | — | — | — | — | — | Raw model 99 at planned 6.0 | INCONCLUSIVE — not run |
| Copper | Protection IV netherite | — | — | — | — | — | Raw model 99 | INCONCLUSIVE — not run |
| Iron | None | — | — | — | — | — | Raw model 115 at planned 6.0 | INCONCLUSIVE — not run |
| Iron | Full iron | — | — | — | — | — | Raw model 115 at planned 6.0 | INCONCLUSIVE — not run |
| Iron | Full diamond | — | — | — | — | — | Raw model 115 at planned 6.0 | INCONCLUSIVE — not run |
| Iron | Full netherite | — | — | — | — | — | Raw model 115 at planned 6.0 | INCONCLUSIVE — not run |
| Iron | Protection IV netherite | — | — | — | — | — | Raw model 115 | INCONCLUSIVE — not run |
| Golden | None | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Golden | Full iron | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Golden | Full diamond | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Golden | Full netherite | — | — | — | — | — | Raw model 85 at planned 6.0 | INCONCLUSIVE — not run |
| Golden | Protection IV netherite | — | — | — | — | — | Raw model 85 | INCONCLUSIVE — not run |
| Diamond | None | — | — | — | — | — | Raw model 130 at planned 6.0 | INCONCLUSIVE — not run |
| Diamond | Full iron | — | — | — | — | — | Raw model 130 at planned 6.0 | INCONCLUSIVE — not run |
| Diamond | Full diamond | — | — | — | — | — | Raw model 130 at planned 6.0 | INCONCLUSIVE — not run |
| Diamond | Full netherite | — | — | — | — | — | Raw model 130 at planned 6.0 | INCONCLUSIVE — not run |
| Diamond | Protection IV netherite | — | — | — | — | — | Raw model 130 | INCONCLUSIVE — not run |
| Netherite | None | — | — | — | — | — | Raw model 145 at planned 6.0 | INCONCLUSIVE — not run |
| Netherite | Full iron | — | — | — | — | — | Raw model 145 at planned 6.0 | INCONCLUSIVE — not run |
| Netherite | Full diamond | — | — | — | — | — | Raw model 145 at planned 6.0 | INCONCLUSIVE — not run |
| Netherite | Full netherite | — | — | — | — | — | Raw model 145 at planned 6.0 | INCONCLUSIVE — not run |
| Netherite | Protection IV netherite | — | — | — | — | — | Raw model 145 | INCONCLUSIVE — not run |

---

## Lunge Boost

### Source-model notes

Vanilla 26.1.2 Lunge applies a server-side horizontal impulse of approximately `0.458 * level` after piercing attack, subject to vanilla eligibility conditions. The current Packet Lunge implementation sends legitimate STAB first, waits a full tick boundary, then requests one collision-checked forward movement capped at 8.5 blocks. The mod does not claim that local velocity changes amplify vanilla Lunge.

| Lunge level | Source vanilla impulse | Vanilla displacement observed | Requested displacement | Final accepted displacement | Vertical displacement | Corrections | Ping | Runtime result |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| I | 0.458 | — | 8.5 | — | — | — | — | INCONCLUSIVE — not run |
| II | 0.916 | — | 8.5 | — | — | — | — | INCONCLUSIVE — not run |
| III | 1.374 | — | 8.5 | — | — | — | — | INCONCLUSIVE — not run |

For any future stronger Lunge mode, require at least 20 consecutive activations with accepted final positions and zero correction packets before exposing it.

---

## Reach

### Source-model notes

Current Smart Reach stages `-9 -> +9 -> STAB -> origin` along the same server-target direction used for packet-only pre-rotation. If both movement requests are accepted, the source model predicts a final forward known-movement component of 18 blocks. With the attacker temporarily 9 blocks forward at STAB time and a 4.5-block spear max range, the source-model distance from the original position is about **31.5 blocks**.

The current UI/controller does not expose a 50–500+ mode. Rows above the conservative acquisition range are intentionally retained as verification/investigation gates rather than implied capabilities.

| Original target distance | Target tracked/visible | Target hurt | Corrections | Final attacker position | Source prediction | Runtime result |
|---:|---|---|---:|---|---|---|
| 5 | — | — | — | — | Within 31.5 source-model envelope | INCONCLUSIVE — not run |
| 10 | — | — | — | — | Within 31.5 source-model envelope | INCONCLUSIVE — not run |
| 25 | — | — | — | — | Within 31.5 source-model envelope | INCONCLUSIVE — not run |
| 50 | — | — | — | — | Outside current Smart source-model/acquisition range | INCONCLUSIVE — not run |
| 100 | — | — | — | — | Not exposed by current Smart mode | INCONCLUSIVE — not run |
| 250 | — | — | — | — | Not exposed by current Smart mode | INCONCLUSIVE — not run |
| 500+ | — | — | — | — | Not exposed; entity tracking may independently limit the experiment | INCONCLUSIVE — not run |

---

## Obstacle/environment cases

Run these separately for each applicable conservative mode. A blocked route must abort before sending the staged movement sequence.

| Case | One-Tap | Lunge Boost | Reach | Corrections | Notes | Runtime result |
|---|---|---|---|---:|---|---|
| Open air / clear flat ground | — | — | — | — | Baseline | INCONCLUSIVE — not run |
| Full wall between stage and return | — | — | — | — | Collision probe should reject unsafe route | INCONCLUSIVE — not run |
| Slab / step | — | — | — | — | Check AABB sampling and server collision | INCONCLUSIVE — not run |
| Water | — | — | — | — | Vanilla Lunge should be ineligible in water | INCONCLUSIVE — not run |
| Narrow corridor | — | — | — | — | Check side collision / bounding-box clearance | INCONCLUSIVE — not run |

---

## Failure record template

When a source-supported technique fails, append a record with all of these fields rather than patching around the evidence:

```text
Expected source path:
Actual observed result:
Client correction evidence:
Server/target observation:
Likely invalid assumption:
Next source location to inspect:
```

## Exposure gates

**Aggressive mode status: NOT EXPOSED — first-five packet timing not runtime-verified.**

No `MAXIMUM`, map-scale, or similar user-facing mode may be added until the required vanilla-server rows pass. The current feature names in the UI correspond only to implemented conservative sequences; they are not runtime-verification badges.
