# Secondary insects implementation plan R2

This extends the original Tasks 0-20.

## Task 21 - population identity model

Add stage, instar, sex, caste/morph, maturation, reproductive and optional feeding
state. Keep locomotion species/stage-specific.

## Task 22 - empirical Argentine-ant worker variation

Replace visually cloned workers with stable correlated target-species morphology.
Do not create fake major/minor/soldier categories for *L. humile*.

## Task 23 - Argentine-ant adult colony forms

Add separate worker, callow, queen/gyne and male rigs/states. Queen count comes from
a documented species/social context rather than a constant of one.

## Task 24 - actual Argentine-ant brood

Implement egg, worker L1-L3 larvae, worker pupa and male pupa from direct
target-species dimensions. Queen brood remains quantitatively gated. Brood never
uses adult locomotion.

## Task 25 - full secondary-insect life-stage inventories

Model the real sex/instar/reproductive/teneral/feeding states for Drosophila,
German cockroach, bed bug and red flour beetle. Ordinary desktop spawning still
filters by ecological context.

## Task 26 - raw-data extraction pipeline

Consume the verified 509,971,606-byte empirical mirror through versioned extractors.
Every derived runtime distribution stores source record, file SHA-256, life stage,
experimental context and directness. Cue-conditioned data cannot silently become
baseline locomotion.

## Task 27 - future polymorphic ant modules

Build *Solenopsis invicta*, *Atta cephalotes* or a selected *Pheidole* species only
as separate profiles. Their majors/minors/soldiers/allometry must never contaminate
the Argentine-ant profile.

## Task 28 - population-composition acceptance

Add statistical tests for worker morphology distributions, adult caste separation,
brood stage geometry, context visibility and stable per-individual identity. Add 1:1
validation scenes for adult/brood/caste comparisons. Diversity must remain batched
and allocation-safe.

## Release invariant

A species may ship with fewer enabled forms if a life-stage/caste evidence gate is
open. It is better to keep an unqualified form disabled than to fill missing biology
with a scaled mesh or a borrowed gait.
