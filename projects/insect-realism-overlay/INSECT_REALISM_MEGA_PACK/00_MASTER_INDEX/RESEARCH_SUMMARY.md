# Research Summary — Primary Ant Evidence Pass

## Method

This first pass contains **39 traceable sources** totaling **2343 reviewed page-equivalent**. The page-equivalent figure measures the extent of material relevance-screened and section-level reviewed. It is not a claim that every sentence of every long book or dissertation was read line-by-line. Numerical simulator parameters are promoted only when their relevant primary passage, table, figure, or dataset has been inspected directly. Copyrighted or license-uncertain bytes remain reference-only.

## Strongly established motion constraints

Worker ants do not move like generic particles. Across 12 species, the alternating tripod pattern persists over a broad range of speed, and curved walking shortens the inside stride rather than merely rotating an unchanged walk cycle [zollikofer-1994-speed-curvature]. Morphology matters: footprint geometry scales with leg dimensions, stride-length versus speed relationships differ among species, and fast ants can enter aerial phases [zollikofer-1994-body-morphology]. Therefore the runtime should drive leg phase and stride geometry from speed/curvature rather than play a single looping sprite.

Speed is not one constant. In Cataglyphis fortis, stride and stance variables change continuously with speed and high-speed motion crosses into running/aerial phases [wahl-2015-cataglyphis-walk-run]. Saharan silver ants reach an extreme envelope of roughly 40–47 Hz stride frequency, ~7 ms minimum stance phases, and about 1.3–1.4 m/s leg swing speed [pfeffer-2019-silver-ant-speed]. Those values are **upper-envelope evidence, not defaults** for the tiny household-ant profile.

For the actual 2–4 mm target, Linepithema humile is especially attractive: independent identification sources put workers around **2.2–2.6 mm** [msstate-linepithema-humile; ufifas-argentine-ant], while the primary taxonomic paper supplies worker head/scapes/leg measurements across a large sample [wild-2004-linepithema-taxonomy]. On uneven terrain, Argentine-ant preferred/peak speed falls by as much as **42%**, mainly via stride-frequency rather than stride-length modulation [clifton-2020-uneven-substrates].

## The monitor surface should be treated as glass, not as a webpage

Close-range Argentine-ant walking changes surprisingly little without vision: flat-ground speed decreased only about **5%** in darkness, and antennal activity responded to terrain structure rather than illumination [clifton-2020-vision]. This is important for the illusion: a YouTube button, text glyph, or bright image is light beneath glass, not a raised obstacle. Ants should not understand UI semantics.

Vertical/smooth-surface biomechanics are real and asymmetric. Adhesive-pad and tarsal-hair experiments show different pulling/pushing roles for feet during vertical climbing [endlein-federle-2015-climbing]. Fire ants around **3.5 ± 0.5 mm** can move at more than **9 body lengths/s** in confined vertical tunnels and use appendage/antenna contact for rapid slip recovery [gravish-2013-confined-locomotion]. These sources justify a distinct climbing/contact model instead of pretending vertical motion is just a rotated floor animation.

## Antennae are part of the motion signature

Carpenter-ant high-resolution tracking separates probing, sinusoidal and trail-following antenna/body modules. Left and right antennae are anti-correlated, and bilateral antenna position predicts a subsequent trail-following body turn with a best-fit lag of about **133 ms** [draft-2018-antenna-tracking]. That makes static antennae or perfectly mirrored oscillation visibly wrong even when the body is tiny.

## Paths should be irregular but structured

Real search paths are not Bézier splines plus random noise. Multiscale analysis of desert-ant trajectories finds Brownian/random-walk signatures across more than two orders of path scale, with context-dependent deviations near goals and after experience [wolf-2024-tortuosity]. Trail-following ants add another structure: Linepithema humile changes heading according to local pheromone asymmetry while speed is comparatively unaffected [perna-2012-argentine-trail-rules]. These should become different simulator states, not blended into one wander function.

## Social motion is encounter-driven, not flocking-for-show

Brief antennal/olfactory encounters provide local information and encounter rate can regulate collective activity [gordon-2020-encounter-rate]. Natural ant traffic can maintain nearly density-independent average velocity without the classical jammed phase seen in vehicle traffic [john-2009-ant-traffic]. A large annotated colony dataset adds 712 tracked ants, 5,354 frames and 114,112 annotations for later interaction statistics [wu-2022-ant-colony-trajectories].

## Visual anatomy and data assets

AntWeb provides programmatic specimen/taxon/image access and standardized head/dorsal/profile views [antweb-2026-api]. Its site-level content is CC Attribution but individual images can carry additional terms, so every shipped image must retain photographer/specimen/source provenance and undergo item-level review [antweb-2026-about-license]. Micro-CT work provides true 3D ant cybertype references and linked volumetric data, valuable for silhouette/proportion validation even when the species is not our final target [hita-garcia-2017-terataner-microct; dryad-terataner-sk6s0].

## What varies by species

Absolute speed, stride frequency, leg proportions, aerial-phase thresholds, pheromone use and navigation strategy vary dramatically. Cataglyphis is invaluable for gait/navigation biomechanics but cannot donate raw speed values to Linepithema without size/species normalization. The first app profile should therefore be a Linepithema-like 2.2–2.6 mm worker whenever direct data exist, with cross-species borrowing explicitly labeled in later derived tables.

## What matters visually at 2–4 mm

At monitor scale the highest-value cues are body length/silhouette, leg-contact cadence, turn asymmetry, irregular acceleration/pauses, antenna motion, contact darkening and coherent multi-ant interactions. Micro-setae, fine eye facets and microscopic pad deformation matter as **mechanical constraints** but generally should not consume per-ant rendering cost unless the ant is magnified. The eventual renderer should spend GPU budget where humans can actually see the error.

## Display scaling

Logical pixels are not physical millimeters. macOS exposes a per-screen backing scale, while Windows has per-monitor DPI-awareness behavior and DPI-change events [apple-nsscreen-backing-scale; microsoft-per-monitor-dpi]. The final app must maintain an explicit millimeter-to-pixel conversion layer with a calibration fallback when reliable physical monitor dimensions are unavailable.

## Remaining uncertainties for the next tasks

1. Pick and normalize the strongest physically calibrated Linepithema trajectory dataset.
2. Extract exact stride distributions for a 2–4 mm profile rather than borrowing Cataglyphis extremes.
3. Measure pause-duration/state-transition distributions from tracked data.
4. Verify dataset-level licenses before bundling any Dryad/Zenodo/video bytes.
5. Quantify which anatomical details survive 96–254 PPI at normal viewing distance.
6. Keep Linux/Wayland overlay limitations separate from biological research until the platform dossier.
