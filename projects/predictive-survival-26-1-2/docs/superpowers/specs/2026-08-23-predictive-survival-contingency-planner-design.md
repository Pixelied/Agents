# Predictive Survival Contingency Planner Design

**Date:** 2026-08-23

## Goal

Upgrade Predictive Survival 26.1.2 from a strong single-action survival planner into a configurable, bounded contingency planner that can reason across stacked lethal threats, proactively prepare multiple server-valid rescue actions, and continuously re-evaluate its remaining plan as live threat state changes.

## Non-negotiable guarantees

- Minecraft Java 26.1.2, Fabric, client-side only, Java 25.
- Preserve the existing vanilla-faithful damage/timeline simulator and fail-closed philosophy.
- Never treat a complexity cap, unknown server state, or missing observation as proof of safety.
- Every production-dispatchable action must have a real executor and server-valid command path.
- Unsupported movement, block-placement, and pearl-rescue model actions remain non-dispatchable.
- Full threat recapture and plan validation still happens every client tick; event dirtiness may accelerate re-evaluation but never replace the tick fallback.
- A rescue policy may restrict what the planner is allowed to do, but must never silently widen itself beyond the configured policy.

## 1. User rescue policy and configuration

Replace the current sparse configuration with a versioned immutable policy model.

### Profiles

- `TOTEM_ONLY`: only death-protection actions are legal.
- `TOTEM_AND_SHIELD`: death protection and shield actions are legal.
- `CONSERVATIVE_SMART`: all enabled production-safe actions are legal, but planner preferences strongly minimize consumables and disruption after survival/reliability.
- `SMART`: all enabled production-safe actions are legal and planner favors larger survival margin while still respecting costs.
- `CUSTOM`: individual action-family toggles and preferences are used directly.

### Custom controls

- Enable/disable death protection, shields, defensive consumables, and emergency equipment swaps independently.
- Death-protection hand policy: allow offhand, allow main-hand takeover, allow proactive dual-hand protection, allow refill from hotbar/inventory.
- Allow/disable automatic restoration of the pre-emergency hand/slot state once the whole danger window is safe.
- Allow inventory routing for enabled non-totem actions when a server-valid route exists.
- Preserve named/custom item identity through existing component fingerprints; never consume a different stack because it shares the same item key.
- Keep debug diagnostics optional.

The config store becomes schema-versioned and migration-tolerant. Missing newer fields receive safe defaults instead of resetting the entire config.

## 2. Candidate generation and routing

Candidate generation becomes policy-aware.

- Death-protection candidates may be generated even when one hand already has protection if the timeline proves a second protection item is necessary before another can be refilled after a predicted pop.
- Shield, consumable, and equipment candidates may be routed from held slots, hotbar, or menu inventory only when the required selection/swap/use sequence can be represented by an existing or newly-added production executor.
- Candidate generation must not create actions forbidden by the active rescue policy.
- Candidate generation remains bounded by engine limits.

## 3. Bounded contingency planning

Introduce a short sequence model rather than a general unbounded search.

A `ContingencyPlan` contains 1-3 `PlannedStep` entries. Each step stores the survival action, conservative activation/completion timing, and the threat state it is intended to cover.

Search behavior:

1. Simulate baseline full timeline.
2. Evaluate legal single actions exactly as today.
3. If no single action guarantees survival, expand only feasible/surviving-prefix actions into a second step, and at most a third step when still required.
4. Apply each step at its conservative server-authoritative completion point, carrying forward health, absorption, effects, blocking state, hurt state, consumed death protection, equipment, and timeline state.
5. Prefer the shortest sequence that guarantees survival. Then compare reliability, final health/absorption, consumable cost, and disruption according to the active profile.
6. Bound node expansions. If the bound is exhausted before a guarantee is found, return no guaranteed sequence rather than assuming safety.

Primary required scenarios:

- Arrow blocked by shield followed by lethal mace requiring a totem.
- Current offhand totem expected to pop, followed too soon by a second lethal hit: proactively arm a second totem in the other hand when policy allows.
- A threat disappears or changes course: remove now-unnecessary future steps while retaining already-authoritative useful state.
- A new lethal threat appears while step 1 is executing: recompute the remaining sequence from actual execution progress.

## 4. Live revalidation

The current tick-driven capture remains authoritative. Add a cheap threat-dirty signal so packet/entity updates can request re-evaluation on the next safe client-thread opportunity without waiting for unrelated work.

When the active timeline changes:

- Re-simulate the in-flight step using the executor's real `remainingServerTicks`, even if the threat schedule fingerprint changed.
- Never pretend an in-flight action restarted from zero merely because a projectile changed trajectory.
- If the current step still contributes to a guaranteed surviving sequence, keep it.
- If it no longer helps or no longer guarantees survival, replace/cancel only when the replacement is safer and executable.
- Restoration is suppressed until the complete remaining danger timeline is safe without the emergency state.

## 5. Mod Menu UX

The Mod Menu screen becomes a compact categorized screen, not a cockpit.

Top-level controls:

- Rescue Profile
- Restore Previous Hand/Slot
- Debug HUD

When `CUSTOM` is selected, show grouped controls for:

- Totems / death protection
- Shields
- Defensive consumables
- Emergency equipment
- Inventory routing / proactive dual protection

Each option has a short tooltip explaining exactly what the planner may do. Unsupported movement/clutch/pearl actions are not shown.

Changes are saved atomically and applied live through `LiveConfigController`; applying a new config clears/replans the current plan immediately.

## 6. Diagnostics

Debug output should display enough information to understand multi-threat decisions without spamming normal users:

- up to the most relevant predicted threats with impact windows;
- active profile;
- current contingency steps (`Shield -> Totem`, etc.);
- current step execution state and remaining conservative server ticks;
- reason for replanning/replacing a step.

## 7. Testing and acceptance

Add deterministic unit regressions for policy filtering, config migration, proactive dual totems, two-step arrow-then-mace survival, three-step bound behavior, changing projectile schedules, in-flight progress preservation, restoration suppression, inventory-routed actions, and fail-closed expansion limits.

Add exact-runtime GameTests for the production routes that depend on Minecraft inventory/use behavior. Existing exact-runtime tests and production JAR isolation must remain green.

Final acceptance requires:

- `./gradlew --no-daemon clean test build`
- `./gradlew --no-daemon compileGametestJava processGametestResources`
- `xvfb-run -a ./gradlew --no-daemon --console=plain runClientGameTest`
- production JAR isolation check
- repository workspace validation
- fresh GitHub Actions success from the final feature head.
