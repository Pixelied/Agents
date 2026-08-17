# SDD ledger — plan: docs/superpowers/plans/2026-08-17-predictive-survival-26-1-2.md

Execution mode: remote isolated branch `feat/predictive-survival-26-1-2` because the ChatGPT container cannot reach GitHub for a local worktree. GitHub Actions is the authoritative build/test environment.

Task 1: complete (commits `bd6195c..5dafee1`; RED run `32052811710`, GREEN run `32052984654`, review-fix run `32053203812`).
Task 1 minor deferred: upstream GitHub Action Node-runtime deprecation warnings; non-blocking and unrelated to Fabric/Java correctness.

Task 2: complete and reviewed. RED `36df8cea` / run `32053553056`; GREEN `4194e4f6` / run `32053751361`. Pure immutable simulation domain added with defensive-copy tests and no Minecraft entity/world references.

Task 3: complete and reviewed. RED `e96841bf` / run `32054024147`; GREEN `ac1a99f3` / run `32054229217`. Vanilla preprocessing order, blocking, freezing, helmet multiplier, non-finite sanitation, hurt cooldown and conservative unknown-lastHurt behavior are locked by tests.

Task 4: complete and reviewed. RED `5ff993ad` / run `32054784976`; initial GREEN `d49be541` exposed one invalid test fixture, not a production bug. Systematic debugging showed Resistance IV made the 8-raw-damage fixture nonlethal; only that test input changed to raw 25 in `2062a1da`. Verification run `32055202389` passed tests/build/artifact upload. Source-driven correction: armor durability happens before armor reduction, so the regression uses armor that survives hit 1 and breaks before hit 2, with hurt cooldown explicitly expired between hits.

Task 5: in progress — conservative shadow tracking of server raw `lastHurt` and invulnerability-time confidence.
