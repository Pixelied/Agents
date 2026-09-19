# Ant population, caste and development model

## Current species: Linepithema humile

The existing ant remains the Argentine ant. Do not import fire-ant or leafcutter
worker castes into this species.

### Adult worker variation

Workers are monomorphic/unimodal, but not clones. Wild 2004 supplies target-species
morphometrics for worker, queen and male forms. Worker population rendering should
sample stable, correlated morphology rather than apply one uniform global scale.

Selected worker ranges from the current research ledger:

- head length 0.62-0.78 mm;
- head width 0.53-0.72 mm;
- scape length 0.62-0.80 mm;
- profemur 0.52-0.68 mm;
- hind tibia 0.57-0.76 mm;
- pronotal width 0.35-0.47 mm.

Queen and male are separate rigs, not scaled workers.

### Actual "baby ant" stages

Solis et al. 2010 directly describes target-species immatures. The study used
150 eggs, a 525-larva sample to determine three instars, and 90 pupae.

| Form | Direct measurement | Runtime rule |
| --- | --- | --- |
| egg | 0.30 +/- 0.03 mm long; 0.23 +/- 0.01 mm wide | nest-only, non-locomotor |
| worker L1 larva | 0.62 +/- 0.08 mm long; head width 0.18 mm | legless brood rig |
| worker L2 larva | 1.18 +/- 0.26 mm long; head width 0.24 mm | legless brood rig |
| worker L3 larva | 1.59 +/- 0.41 mm long; head width 0.26 mm | legless brood rig |
| worker pupa | 2.41 +/- 0.09 mm long; head width 0.69 +/- 0.03 mm | exarate/no cocoon, nest-only |
| male pupa | 3.18 +/- 0.12 mm long; head width 0.66 +/- 0.03 mm | separate sexual pupa |
| queen larva/pupa | quantitative gap | keep calibrated release gate closed |

After eclosion, callow workers are a separate maturation/material state rather than a
miniature ant. The research branch keeps exact callow timing/behavior conservative
until direct measurements are available.

### Queen count / colony social form

Do not hard-code one queen per colony. Argentine ants are polygynous, and queen
number changes with ecological/social context. The population generator must choose
a documented context and preserve it in diagnostics.

### Surface visibility

Normal exposed-desktop mode strongly favors mature workers. Brood are nest-only;
queens are nest-dominant; males/gynes are reproductive-event forms. A future nest
mode can expose colony composition without causing larvae or eggs to roam on glass.

## Future visibly polymorphic ant profiles

These are separate future species:

- **Solenopsis invicta**: data-rich worker polymorphism/allometry; roughly 2-5.5 mm
  worker body-length range across studied colony/social contexts; shape changes with
  size and minor/major population structure.
- **Atta cephalotes**: extreme worker polymorphism; reported worker head widths about
  0.6-4.5 mm with size-linked colony tasks.
- **Pheidole**: usually distinct minor/major worker subcastes; choose a concrete
  species before numeric implementation.

Rule: `species -> social form -> caste/morph -> individual morphology -> behavior`.
Never pick a visual scale first and invent a caste label afterward.
