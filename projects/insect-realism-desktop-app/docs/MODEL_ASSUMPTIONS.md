# Runtime model and explicit assumptions

The runtime consumes the supplied compiler's `2026.09.17-donor-transfer.2` bundle.
The donor is **Temnothorax rugatulus, tandem leader**, not Argentine-ant motion
measured on a monitor. The 20 retained tracks and 53,940 intervals remain intact.
The geometry target is **Linepithema humile**, with the Pack's 2.2-2.6 mm worker
body-length range. No source article, thesis, reference video, or image is copied
into this application's runtime assets.

## Numerical/scheduling policy (not biological measurements)

* 30 Hz fixed biological ticks, integer rational accumulation, at most eight
  catch-up ticks, and discarded elapsed sleep/stall time. Rendering interpolates
  independently. Decision staggering uses fixed tick and stable creature id.
* Preallocated structure-of-arrays columns, live/free slot lists, and cached
  render-state output. Snapshot serialization and geometry changes may allocate;
  warmed simulation steps do not.
* Spatial queries are exact for tools/tests. Encounters examine at most 128
  candidates per creature and are considered every third biological tick. This
  is a bounded computational budget, not a sensory/biological limit.

## Evidence transfers and implementation assumptions

* Donor speed is normalized by the donor median and scaled by the target's
  condition-specific reference speed. Heading interpolates the reported flat,
  upward, and downward means. This is an **engineering cross-condition and
  cross-species transfer**, not a validated distribution of ants on coated glass.
  The profile's signed acceleration and angular-rate ranges bound integration.
  Original track order/timing is retained; movement is not random-waypoint or
  continuous steering-noise AI.
* Stable individual length, width, stride, persistence, pause duration, and speed
  variation use profile ranges. Uniform range sampling is an engineering prior;
  the statistical test checks this implementation prior, not biological fit.
* An encounter is considered once per new partner. The profile explicitly marks
  radius and response probability as assumptions. Opposed headings favor an
  encounter stop; near overlap favors avoidance; parallel traffic can follow.
  These choices are qualitative reconstruction, not measured outcome frequencies.
* Antenna targets use the profile's explicitly assumed sweep/timing/filter
  parameters. Left/right targets have 0.8 anti-correlated and 0.2 independent
  weights, transit sweep width is multiplied by 0.55, and encounter bias starts
  at 0.45 radians. Those reconstruction weights are **engineering assumptions**
  inspired by, not measured in, `paper-draft-2018-antennae`. Targets persist and
  are filtered; there are no independent antenna sine loops.
* Walking distance advances gait by distance / sampled stride length. Stop means
  zero gait advancement after velocity settles. The 0.0001 mm/s settle threshold
  is numerical precision policy, not a measured rest threshold.
* Trail intensity is normalized affinity, not a chemical concentration. Deposit
  is dt / profile decay time, scaled by the user trail strength. Directional and
  left/right antenna samples affect turning, not preferred walking speed.
* Normal entrants begin half a maximum body length outside a physical edge.
  Population reduction marks residents for departure; it does not pop them out
  of the middle. Removed monitors discard their no-longer-visible residents.
* Edge investigation/turning blends into tangent/inward motion; there is no
  hard-circle bounce. Outer exits are clipped by the overlay. Continuous edges
  preserve mm/s velocity, identity, and heading; interpolation history is rebased
  to the receiving display to avoid a cross-screen streak.
* Pose blends (walk 0, pause 0.3, probing/edge 0.6, encounter 1) are render controls,
  not biological measurements. Grooming remains disabled: evidence does not
  justify adding a numerical grooming model.

Every physical parameter read by `Biology` has an `EvidenceRef` in the compiled
bundle. Its source path, field, SHA-256, species/condition, license classification,
and measured/derived/donor/engineering basis are preserved in the bundle report.

## Procedural renderer reconstruction (Tasks 9-11)
The Pack provides independent worker head/body dimensions and qualitative anatomy,
not a calibrated articulated 3D mesh. The renderer keeps measured body/head extents
and distributes the remaining length between thorax and gaster using the existing
profile priors as relative weights. Neck/waist length fractions (0.025/0.045),
elliptical masses, limb-root/joint placements, per-seed gaster width adjustment,
antennal scape/flagellum lengths and the 1.15-radian elbow are explicit engineering
reconstruction, not measured target-species constants. Antennae remain driven by
correlated simulation targets, not independent sine loops. The magnified fixtures
exist to find anatomy defects, not to claim microscopic realism from three ellipses.

Six legs use the profile stride and stance fraction: during stance the local foot
moves backward by exactly the corresponding body displacement. Swing is eased;
its projected lateral lift is an artistic reconstruction. Geometry blends between
simplified/segmented limbs through a physical-pixel threshold to avoid LOD popping.
The strip-coverage approximation conserves thin-line coverage approximately, not
an exact analytical integral of every pixel/segment intersection. GPU rotation
and limb-only tests bound its observed variation on the tested backend.
