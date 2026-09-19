# Population morphs and life-stage rules

## Drosophila melanogaster

Represent egg, L1, L2, L3, pupa, adult female and adult male. Immatures are
breeding-substrate entities and use larval/pupal rigs; they never reuse adult gait.
Adult exposed-surface behavior remains the normal desktop form.

## Blattella germanica

Represent ootheca, nymph instars, teneral/newly molted state, adult female and adult
male. The current high-confidence movement model is first-instar specific. Later
nymph/adult locomotion requires its own evidence. Instar count must be distributed/
conditioned rather than universally fixed.

## Cimex lectularius

Represent egg, nymph instars 1-5, adult male and adult female. Feeding/engorgement is
an orthogonal morphology/behavior state. The evidence includes instar-specific
antenna/pronotum progression and adult sex morphometrics, but direct adult footfall
timing remains a release gate.

## Tribolium castaneum

Represent egg, variable larval series, pupa, teneral adult, adult male and adult
female. Larval gait is never transferred to adults. Adult whole-body movement,
dispersal/personality and anti-predator evidence are useful, while adult leg-phase
gait remains a hard gate.

## Contexts

Every population form is filtered through a context:
`exposed_surface`, `nest_colony`, `breeding_substrate`,
`reproductive_event`, or `developer_validation`.

Merely existing in a species profile does not grant a stage permission to appear on
an ordinary desktop.
