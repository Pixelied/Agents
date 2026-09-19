# InsectRealism — R8.2 CURRENT Implementation Plan

**Date:** 2026-09-19  
**Phase:** implementation after R8/R8.1 research consolidation and quad audit  
**Authority:** this file is the single current implementation authority. It supersedes the R5/R7/R8 species implementation direction and the temporary R8.1 direction-change note. Preserve newer verified app/debugging work already present in the current workspace.  
**Execution model:** continue directly from the newest cleaned local working project. Do not restart from an old archive.

---

# 0. Mission

Take the current InsectRealism desktop utility and implement the R8.2 evidence-backed arthropod direction without regressing the app's existing safety, physical scaling, ant biology, macOS behavior, performance, or compatibility.

The non-ant population is deliberately small, with **three baseline crawlers plus one optional predator**:

1. *Liposcelis bostrychophila* — adult parthenogenetic domestic-form baseline;
2. *Blattella germanica* — ~24 h first instar;
3. *Oryzaephilus surinamensis* — adult;
4. *Chelifer cancroides* — **adult optional rare pseudoscorpion predator / advanced-ecology species**.

The fourth entry is not a common background crawler and must not be spawned at ant/booklouse densities. It exists because its exact-species locomotion evidence and real indoor predatory ecology are unusually strong. Do **not** implement Drosophila merely because old research exists. Deferred candidates remain gated until new evidence closes their gaps.

The desired illusion remains:

> one tiny crawler appears; it is believable enough to ignore; over long periods a few more arrive; edge/crack routes seem to establish; localized populations form; individuals wander away; eventually multiple terrestrial species can coexist and the screen feels genuinely infested.

Biological accuracy outranks novelty and species count.

---

# 1. Authority, preservation, and evidence hierarchy

## Task 1 — Resolve and protect the actual current workspace

Before changing source:

- identify the exact local project root;
- inspect `git status` if applicable, timestamps, build outputs, config migration state, current docs and platform adapters;
- preserve dirty work with a timestamped patch/backup before editing;
- do not restore an older ZIP/checkpoint over the current cleaned workspace;
- do not fetch/reset remote history as part of this assignment;
- record the initial source state in `APP_EXECUTION_R8_2.md`.

R8/R8.1 research was produced against the cleaned workspace whose architecture already separates `creature-profile`, `display-model`, `settings`, `simulation`, `rendering`, `platform-api`, native adapters, `desktop-app`, and `profile-compiler`. Verify the local tree still matches that intent before proceeding.

## Task 2 — Establish the implementation authority chain

Use this order when documents disagree:

1. this R8.2 implementation plan;
2. R8.2 species profiles + Realistic-mode gates;
3. R8 source/dataset/image manifests and analysis;
4. current local source for present implementation behavior;
5. R3 audited secondary research and current Argentine-ant evidence;
6. current architecture/safety docs;
7. R5/R7 historical docs only for still-valid general work;
8. new evidence only if deliberately audited and appended with provenance;
9. engineering assumptions, labeled and kept outside strict Realistic where biologically meaningful.

Do not edit R3/R8 history to make the current direction look cleaner. Add R8.2 profiles/gates/versioned records and keep superseded documents clearly historical.

---

# 2. Baseline before species work

## Task 3 — Reproduce current app behavior and tests

Build and run the current app/test suite before structural edits.

Capture:

- compiler/toolchain versions;
- unit/integration/native-test results;
- current overlay startup behavior;
- physical-size calibration behavior;
- current ant preset counts and simulation rates;
- current 100/500/1000 creature benchmark numbers;
- current allocation/CPU/GPU/frame timing where instrumentation exists;
- current macOS Spaces behavior;
- click-through/input behavior;
- menu/tray behavior;
- current settings migration and persistence.

If a baseline test already fails, record it as pre-existing rather than silently attributing it to R8.

## Task 4 — Create R8.2 execution ledger

Create `APP_EXECUTION_R8_2.md` with one section per task:

- source files changed;
- evidence/profile IDs consumed;
- implementation decision;
- tests run;
- benchmark before/after;
- native verification result;
- remaining blocker/gap.

This ledger is implementation provenance; it is not a replacement for scientific source provenance.

---

# 3. Make R8.2 evidence machine-checkable

## Task 5 — Add versioned R8.2 species qualification data

Do not overwrite historical `SECONDARY_CREATURE_GATE.md` or the R3 audit.

Add an R8 gate/schema that can represent at least:

```text
species
life_stage
sex_or_form
qualification_state
physical_measurements
visual_envelope
body_locomotion
articulated_gait
surface_context
flight_state
population_mechanisms
interaction_permissions
source_ids
evidence_class
hard_gaps
realistic_mode_permissions
experimental_permissions
```

The profile compiler/validator must reject:

- unknown evidence classes;
- units omitted from numeric calibration;
- stage mismatch hidden as direct evidence;
- cross-species donor values labeled as direct;
- distributions synthesized from a single mean without explicit engineering classification;
- body scale with no provenance;
- Realistic behaviors that a profile marks blocked.

## Task 6 — Separate body locomotion from articulated gait

This is a major R8 correction.

A species may be qualified for whole-body translation/turn/pause behavior while articulated leg gait remains unqualified.

Represent at least three states independently:

- body locomotion: qualified / partial / blocked;
- articulated gait: qualified / renderer-prior-only / blocked;
- interaction response: qualified / experimental / blocked.

Do not use a renderer's plausible leg animation as proof of biological gait calibration.

---

# 4. Preserve the display model and make visual footprint first-class

## Task 7 — Add physical-footprint validation tooling

Keep the existing `display-model` as the owner of millimetres, calibrated pixels and display topology.

Add tests/tooling that report, for each species/form:

- body length/width in mm;
- measured or bounded appendage envelope when available;
- derived envelope values clearly marked as derived;
- expected body/envelope pixels at 96, 110, 144 and 220 PPI;
- actual runtime px values on detected calibrated displays.

Required reference checks include:

- *L. bostrychophila* 0.96–1.15 mm body;
- *B. germanica* first instar 3.0 x 2.0 mm body, 3.0 mm antenna;
- *O. surinamensis* 2.5 mm adult anchor;
- *C. cancroides* adult body: direct study mean 3.09 ± 0.47 mm, observed 2.13–3.84 mm (n=10);
- *C. cancroides* pedipalp length: direct study mean 3.88 ± 0.72 mm, observed 2.98–5.45 mm;
- *C. cancroides* total extended crab-like span: **7–9 mm** from Penn State Extension, treated as a total visual-footprint constraint rather than body length;
- explicit pixel-footprint reporting for the optional predator so its larger appendage envelope is never silently shrunk to match the micro-crawler layer.

### Acceptance

- no Realistic species silently uses a visibility scale multiplier;
- logical UI scale cannot change organism physical size;
- per-display PPI conversion remains stable across monitor boundaries;
- unknown appendage span remains unknown, not substituted with an arbitrary constant.

### Reference-corpus authority rule — treat the ~1 GB corpus as visual validation data, not numerical calibration

The consolidated R8.2 handoff contains **1,047,195,171 raw bytes / 1,378 validated exact-species media files** across the four non-ant target taxa. This is primarily visual/reference data sourced through GBIF-backed occurrence media under redistribution-compatible licenses.

Rules:

- do not derive body length, speed, gait, prey rate or development timing from these images;
- exact-species occurrence identification is useful but is not equivalent to manual specimen re-identification by the implementation agent;
- stage-unlabeled media are contextual; they cannot override exact-stage scientific specimen references;
- use the per-file manifest, creator/license/source URL and SHA-256 records;
- duplicated or corrupt bytes must fail audit;
- the empirical CSV/JSON extracts and cited papers control numerical calibration.

The corpus is valuable for silhouette diversity, natural color/material variation, pose ranges, texture reference and spotting obvious renderer mistakes. It is not a license to average arbitrary photographs into morphology constants.

---

# 5. Preserve the Argentine-ant system

## Task 8 — Re-run the ant parameter/provenance audit, but do not restart ant research

Use the existing ant Mega Pack/current compiled profile.

Verify no R8 refactor breaks:

- worker morphology variation;
- target/donor evidence labels;
- trail/recruitment field;
- physical speed units;
- stable individual traits;
- queens and males as distinct adult forms;
- callow workers as young adult workers;
- eggs, worker/male larvae, pupae and other brood identity already supported by the ant evidence pack;
- corrected worker-vs-male larval series;
- no fake major/minor/soldier castes.

Only change ant numerical biology when the new evidence is demonstrably stronger/closer to *Linepithema humile* and a before/after diagnostic is recorded.

## Task 9 — Keep ant renderer variation correlated and stable

Workers should not look cloned, but they remain a monomorphic/unimodal worker caste.

Maintain stable individual seeds and correlated morphological variation. Do not globally scale one ant mesh as the only variation mechanism when evidence supports differing proportions.

Preserve callows as ant-shaped young adults. Eggs/larvae/pupae stay nest-context objects, not surface-walking 'baby ants'.

---

# 6. Slow Burn / Creeping Infestation R8.2 population model

## Task 10 — Replace generic population growth with mechanism-tagged arrivals

Every visible addition/removal should have a mechanism tag such as:

```text
MigrationArrival
HiddenEdgeEmergence
HarborageEmergence
AntRecruitment
HatchOrEclosion
LiteralDevelopment
VoluntaryExit
PopulationDecay
```

Species controllers decide which mechanisms are legal.

Do not allow a universal exponential population spawner in Realistic mode.

## Task 11 — Preserve the near-empty start and irregular escalation

Slow Burn should:

- start with zero or nearly zero visible individuals;
- use sparse, irregular arrival timing;
- establish a small number of persistent entry regions over long periods;
- occasionally introduce scouts/strays from unrelated edges;
- strengthen productive routes gradually;
- allow weak routes to fade;
- reduce visible population through exits rather than popping entities;
- persist infestation age/history only when the user has enabled persistence;
- never increase locomotion speed as a proxy for severity.

### Species mechanisms

**Argentine ant:** migration + trail recruitment + exits.  
**Liposcelis:** hidden arrivals from edge/crevice/stored-material microhabitats; literal development only on measured timescales.  
**B. germanica first instar:** sibling arrivals/emergence from hidden harborage; no accelerated on-screen maturation.  
**O. surinamensis adult:** adult arrivals from stored-product/crack reservoirs; larvae/pupae remain off-screen/gated.  
**C. cancroides adult (optional):** rare arrivals/emergence from quiet indoor cracks/crevices or hidden reservoirs. Its visible population does not grow by compressed reproduction. Prey abundance may influence an **optional engineering arrival-pressure abstraction**, but the app must not pretend it senses real prey odor or a real indoor ecosystem.

## Task 12 — Add time-scale tests

Tests must prove:

- *L. bostrychophila* does not egg-to-adult in minutes; measured lab development is on the order of ~18–42 days across tested temperatures;
- first-instar cockroaches do not mature to adults in a short demo session;
- Oryzaephilus immature stages are not spawned as generic small beetles;
- *C. cancroides* does not egg-to-adult in a demo session: Penn State reports three nymphal molts and temperature-dependent development on the order of **10–24 months**, with adults living roughly **3–4 years**;
- the Penn State 20–40 egg / ~3-week egg-deposit-to-brood-dispersal context is not permission to spawn adult predators rapidly;
- user-facing faster infestation presets increase arrival pressure, not biological developmental speed.

---

# 7. Shared multi-species architecture

## Task 13 — Extend species model types without genericizing biology

Use common infrastructure for:

- fixed-step simulation clock;
- calibrated physical display topology;
- deterministic RNG;
- spatial indexing;
- population scheduler;
- low-level interaction fields;
- GPU batching/LOD;
- provenance/evidence plumbing.

Species-owned code includes:

- behavior state machine;
- locomotion parameters;
- gait renderer/controller;
- morphology;
- life-stage visibility;
- social behavior;
- flight/jump capability;
- disturbance response;
- corpse/squish presentation.

Conceptually:

```rust
enum SpeciesModel {
    ArgentineAnt(...),
    LiposcelisAdult(...),
    GermanCockroachFirstInstar(...),
    SawtoothedGrainBeetleAdult(...),
    HousePseudoscorpionAdult(...), // optional advanced-ecology species
}
```

Do not preserve old Drosophila/bed-bug/Tribolium enum variants as enabled Realistic species merely for backward convenience. If old code exists, keep migration compatibility but gate them from R8.2 presets.

## Task 14 — Make stable individual identity species-neutral but behavior-neutral

A shared identity record may include:

```text
species
life_stage
sex_or_form
stable_variation_seed
spawn_mechanism
birth_or_arrival_time
persistent_id
```

Do not put ant-specific caste/trail fields into every species. Use typed extensions.

---

# 8. Liposcelis bostrychophila implementation wave

## Task 15 — Add evidence profile and exact physical-scale renderer

Target form: adult parthenogenetic domestic-form baseline; do not claim all global populations are female-only because sexual strains are documented.

Body-length authority is strain/form aware:

- classic diagnostic range for parthenogenetic females: **0.96–1.15 mm**;
- directly measured Kansas parthenogenetic strain in the 2015 morphology paper: **0.91–0.95 mm**;
- other exact-species household surveys extend slightly beyond this range.

Do not fabricate one universal population distribution. Bind the renderer to the chosen R8.2 profile/form and preserve provenance for the range used.

Renderer requirements:

- independent psocid body plan; never an ant mesh shrunk down;
- body scale driven by the direct range;
- subtle individual variation only within measured/defensible limits;
- exact appendage envelope remains a hard gap: choose a conservative geometry prior only if clearly tagged as renderer-only and ensure it cannot be mistaken for measured span;
- at 96 PPI the body is only about 3.6–4.3 px long, so antialiasing/coverage stability matters more than decorative detail;
- do not enlarge to make it recognizable.

Use exact-species scientific/specimen imagery only as secondary geometry/material reference.

## Task 16 — Implement conservative booklouse body locomotion

The direct adult study provides an assay-conditioned speed anchor around **3.6 ± 0.2 mm/s**.

Do **not** create a fake precise Gaussian/lognormal 'natural' speed distribution from that number.

Implement one of these safe options:

1. a narrow, explicitly engineering-bounded motion prior centered near the measured assay anchor, marked non-direct in provenance; or
2. keep Realistic booklice in a conservative crawl mode with limited parameterization until more direct per-individual data is ingested.

Required:

- no flight;
- no ant trail following;
- no generic cockroach wall-state reuse;
- no springtail jump;
- no cursor flee by default;
- no claim that an insecticide-treatment assay represents all natural contexts.

## Task 17 — Booklouse habitat/population behavior

Model entry and persistence as a moisture/stored-material/crevice associated infestation **without pretending the app senses actual humidity or food**.

Desktop abstractions may use:

- configured edge/entry-source persistence;
- hidden off-screen reservoirs;
- slow local accumulation;
- occasional dispersal away from source.

Do not map a dark rectangle or browser window to food/mould/humidity.

Literal population development follows evidence-consistent timescales. A faster visible rise comes from arrivals, not accelerated reproduction.

## Task 18 — Booklouse validation gate

Validate:

- body size distribution in mm and px;
- no scale multiplier in Realistic;
- mean translational behavior remains near the allowed calibration boundary;
- no fabricated turn/pause distributions are labeled direct;
- no hidden ant/controller fields influence behavior;
- performance at dense counts, because this is the most likely species to appear in high numbers.

Keep these blocked:

- cursor response;
- articulated target-species gait claims;
- exact edge-follow coefficient;
- species-specific corpse/smear material.

---

# 9. Blattella germanica first-instar implementation wave

## Task 19 — Import the exact R3 first-instar profile into the runtime compiler

Do not retype constants in source where the profile system can compile them.

Required exact-stage parameters from BLA-001/R3:

| Parameter | Value |
|---|---:|
| body length | 3.0 mm |
| body width | 2.0 mm |
| antenna length | 3.0 mm |
| central speed | 11.0 mm/s |
| peripheral speed | 10.6 mm/s |
| central stop hazard | 0.03 s^-1 |
| peripheral stop hazard | 0.08 s^-1 |
| peripheral exit hazard | 0.12 s^-1 |
| transport mean free path | 23.2 mm |
| departure-angle lognormal GM | 36.6° |
| departure-angle geometric SD | 2.14 |
| short-stop state probability | 0.93 |
| short-stop mean | 5.87 s |
| long-stop mean | 700 s |
| peripheral/wall threshold | 5 mm |
| observed sampling interval | 0.68 s |
| paper model dt | 0.2 s |

Preserve the original stop-definition metadata instead of turning these numbers into context-free constants.

## Task 20 — Implement the first-instar bounded-space body model

Use the central/peripheral states and measured transition rules.

Requirements:

- state transitions are fixed-step deterministic under seed;
- physical edge distance uses calibrated millimetres;
- peripheral departures use the audited angle distribution;
- stop mixture is implemented with correct units/hazards;
- antenna-wall contact can influence visual pose only within evidence bounds;
- no adult flight/wings;
- no adult male turning metrics used numerically as nymph gait.

## Task 21 — Renderer: recognizable nymph without fake gait

Build a distinct first-instar cockroach rig:

- 3 mm body / 2 mm width physical anchor;
- long antennae at the correct scale class;
- nymph body proportions/material from exact-stage references;
- leg animation tied to body motion for visual coherence, but explicitly classified as renderer prior until exact-stage footfall data exists;
- no oversized 'baby roach' exaggeration.

Do not present an aesthetically plausible leg cycle as measured gait.

## Task 22 — Cockroach social/interaction gates

R3 says neighbor-density aggregation response needs exact extraction before social arrest enters Realistic mode.

Therefore:

- no generic boids;
- no invented attraction radius;
- no ant pheromone logic;
- no automatic cursor fear;
- no wind/startle transfer from adult/different-stage literature as a first-instar numeric response.

Interactive mode may offer a subtle engineering reaction if labeled as such.

## Task 23 — Cockroach validation

Test statistical reproduction of:

- central vs peripheral speed;
- time spent moving/stopping near periphery;
- stop-duration mixture;
- peripheral exit hazard;
- mean free path;
- departure-angle distribution;
- wall/periphery occupancy;
- physical body/antenna size.

Reject release if the renderer visually slides while the body model is stopped/moving inconsistently.

---

# 10. Oryzaephilus surinamensis implementation wave

## Task 24 — Build the adult sawtoothed grain-beetle profile

Physical anchors:

- adult length ~2.5 mm from Purdue;
- flat crack/crevice-adapted form;
- optional contextual geometry prior from the single ~2.70 mm body / 0.707 mm width / 0.65 mm antenna specimen, clearly labeled as a single exemplar.

Do not convert that one specimen into population variance.

Flight policy:

- Purdue reports developed wings but no record of this species flying;
- Strict Realistic exposed-surface mode remains crawling-only because Purdue reports well-developed wings but **no record of this species flying**;
- wording/docs must say **'no recorded flight in the cited source'**, not 'incapable of flight' or physiologically flightless;
- if future direct evidence documents flight in the target context, the gate must be revisited instead of silently suppressing it.

## Task 25 — Implement direct adult body movement anchors

Mowery direct adult-female assay:

- Cello: 3.49 ± 0.13 mm/s, n=76, 300 s;
- 120 AB-X: 2.74 ± 0.11 mm/s, n=76, 300 s.

Treat substrate as a real source of variation. Do not average the two into a falsely universal constant without labeling the derivation.

Kavallieratos untreated 15-min control:

- walking 532.3 ± 38.9 s;
- 6.5 ± 1.2 stops;
- total stop duration 145.7 ± 29.7 s;
- 10.8 ± 8.6 climbing events;
- climbing time 209.4 ± 29.4 s;
- upturned behavior is rare/minimal in the control.

Use these to constrain body-state timing. They are not articulated gait evidence.

## Task 26 — Create an edge/crack crawler, not a generic beetle random walk

The flat body and crack adaptation justify entry-source geometry and edge-adjacent hiding/emergence at the narrative level.

However, do not invent a quantitative thigmotaxis coefficient unless direct data supports it.

Safe behavior:

- enter from narrow edge/crack sources;
- traverse exposed surfaces at measured body-speed class;
- pause using timing constrained by direct adult control data;
- attempt vertical/boundary climbing only within the measured behavioral category, not with fake glass-adhesion constants;
- exit through cracks/edges;
- no ant trail following;
- no routine flight state in R8.2 Strict Realistic under the current Purdue evidence boundary.

## Task 27 — Beetle renderer and validation

Renderer:

- compact, flat adult;
- recognizable sawtoothed-pronotum silhouette where pixel footprint permits;
- physical scale stays honest;
- exact-species reusable/scientific references guide proportions/material;
- articulated gait remains a visual prior, not a measured target gait.

Validate:

- 2.5 mm scale anchor across PPIs;
- measured body-speed conditions;
- move/stop timing envelope;
- climbing events do not dominate;
- no accidental flight state;
- mixed-population draw/buffer cost.

---

# 11. Chelifer cancroides optional-predator implementation wave

## Task 28 — Add an adult-only optional predator profile with full visual envelope

Correct taxonomy: **Arachnida → Pseudoscorpiones → Cheliferidae**, not Insecta. User-facing copy may say “pseudoscorpion” or “arthropod predator”; do not label it an insect in scientific documentation.

Primary morphology/locomotion authority: Tross et al. 2022, *Journal of Experimental Biology*, DOI `10.1242/jeb.243930`. Their adult sample (n=10; 3 male, 7 female) reports:

- body length 3.09 ± 0.47 mm, range 2.13–3.84 mm;
- prosoma length 0.97 ± 0.16 mm;
- leg lengths L1–L4 means 1.37, 1.47, 1.77 and 2.10 mm;
- pedipalp length 3.88 ± 0.72 mm, range 2.98–5.45 mm;
- body mass 2.48 ± 0.51 mg.

Penn State Extension describes adult body length around 3–4 mm and an extended pedipalp span of **7–9 mm**. Treat that extended span as a real visual-footprint constraint.

Requirements:

- adult baseline only; do not render protonymph/deutonymph/tritonymph as scaled adults without direct stage morphology;
- no “micro mode” scale shrink to make it match ants/booklice;
- stable individual morphology seeds within measured/admissible bounds;
- pedipalp pose is part of the collision/visual envelope, not decorative overflow;
- exact-species imagery is geometry/material reference only after stage/view provenance is checked. The ~1 GB GBIF corpus is **not** measurement authority.

## Task 29 — Implement the unusually strong direct gait evidence without inventing a free-walking speed distribution

Tross et al. directly recorded locomotion at **500 fps** and supports:

- coordinated alternating-tetrapod locomotion during forward and backward walking;
- frequent forward **microstops around 100–200 ms**;
- forward locomotion generally slower than backward escape;
- backward speeds up to about **17 body lengths/s** in the study;
- upside-down walking up to about **4 body lengths/s**, with slower/less rigid coordination and more legs in stance;
- a particularly important stability role for leg pair 2;
- direct stride/stance/swing relationships and footfall-position evidence.

Use this evidence to qualify **articulated adult gait** far more strongly than for the three baseline secondary crawlers. However, do not turn the paper's examples/maxima into a universal desktop free-walking speed distribution. Until the supplemental EthoVision tables/trajectories are ingested and audited, whole-body roaming speed selection remains partial/evidence-gated.

Implementation requirements:

- support forward walk, brief microstop, turn/contact, and fast backward escape states;
- gait phase must be consistent with travel direction;
- do not reuse spider, ant or cockroach footfall cycles;
- upside-down gait is only relevant if the renderer/platform ever depicts underside/ceiling traversal; do not invoke it randomly on a flat display;
- smooth-display traction remains a hard transfer gap.

## Task 30 — Add predation as evidence-classified ecology, not an arcade enemy system

The optional predator exists to create rare cross-species ecology. Evidence boundaries are strict:

- the 2026 *Journal of Apicultural Research* study directly tested *C. cancroides* against **Varroa destructor** and the psocid **Liposcelis entomophila**;
- *L. entomophila* is **not** *L. bostrychophila*. Treat transfer to the rendered booklouse as a **same-genus prey analog**, never direct target-prey evidence;
- Penn State Extension lists small arthropod prey including **ants, beetle larvae, flies/caterpillars and booklice**;
- there is **no direct Argentine-ant (*Linepithema humile*) kill-rate, pursuit-radius, handling-time or preference measurement** in the current evidence pack;
- do not infer that adult *O. surinamensis* or first-instar *B. germanica* are proven prey merely because they are small.

Strict Realistic rules:

- predator presence is optional and very sparse;
- close-range encounter/attack capability may exist only with provenance attached to the prey category;
- do not implement global nearest-prey homing;
- do not invent odor/heat/host sensing from screen pixels;
- no prey-specific kill probability or preference ranking without evidence;
- if an Argentine-ant-specific predation interaction is enabled before direct calibration exists, classify the quantitative encounter/attack constants as an **engineering abstraction** and expose it only in the optional Ecology/Interactive behavior layer, not as direct biology.

A good default is: Strict Realistic permits natural wandering and evidence-bounded close-contact predator behavior; **Optional Ecology** may enable visible ant/booklouse predation with every non-measured rate labeled as engineering.

## Task 31 — Keep predator population slow, rare and performance-safe

Life-history context from Penn State:

- females carry roughly 20–40 eggs;
- young pass through protonymph, deutonymph and tritonymph stages;
- egg/juvenile development to adulthood takes roughly 10–24 months depending on temperature;
- adults can live roughly 3–4 years;
- the brood remains with the female briefly before dispersal.

Therefore:

- do not create rapid on-screen predator reproduction;
- visible increases come from rare new arrivals/emergence from hidden reservoirs;
- normal Realistic/Slow Burn scenes should commonly have **zero** pseudoscorpions, occasionally one, and rarely more unless explicitly configured;
- a prey-rich screen may increase optional arrival pressure only as a labeled ecological abstraction, not fake sensing;
- add dedicated performance tests at realistic 0/1/2/5 counts plus stress-only 20/100 counts. A 100-predator test is a renderer stress case, not a biological release density.

Validate:

- physical body and 7–9 mm extended visual span across reference PPIs;
- no silent scale shrink;
- forward/backward gait direction and microstop behavior;
- no global homing;
- predation evidence class is target-specific/same-genus/general-category/engineering as appropriate;
- rare-arrival population schedule;
- no reproduction-time compression.

---

# 12. Enforce rejected/deferred candidates

## Task 32 — Remove old implementation authority for Drosophila, bed bug and Tribolium

Do not delete historical research. Do remove them from:

- default/Realistic species presets;
- 'next wave' comments that imply they are R8 targets;
- qualification tables that mark them ready by historical status alone;
- docs/screens that suggest Drosophila is the primary secondary species.

If legacy settings contain those species, migrate safely to disabled/legacy/experimental status rather than crashing or silently mapping them to another species.

## Task 33 — Encode future-candidate gates

Future candidates should be represented as research-gated entries, not half-implemented controllers:

- *Willowsia nigromaculata*: requires direct crawl/jump distributions;
- *Gibbium psylloides*: requires quantitative trajectories;
- *Cartodere constricta*: requires locomotion + species-specific flight status;
- *Cheiridium museorum*: requires exact-species gait and pedipalp envelope;
- *Bryobia praetiosa*: arrival-only ecology; requires locomotion/footprint data;

A test should fail if any still-deferred candidate is enabled in strict Realistic without a versioned gate change. *Chelifer cancroides* is no longer in this deferred list; it is an optional R8.2 predator with its own gate.

---

# 13. Cursor, click, disturbance and screen field

## Task 34 — Replace generic cursor response with per-species permission

Create a policy table in code/docs.

Recommended R8.2 strict defaults:

| Species | Strict Realistic cursor response |
|---|---|
| Argentine ant | existing evidence-gated behavior only |
| L. bostrychophila | off |
| B. germanica first instar | off unless a direct target-stage analog is added |
| O. surinamensis adult | off |
| C. cancroides adult | off unless a direct target-species disturbance analog is added |

Interactive mode may use subtle engineering abstractions, but profile provenance must say so.

No instant 180° arcade turns. No cross-monitor response when the cursor is physically unrelated.

## Task 35 — Keep click-to-squish optional and input-transparent

Preserve the underlying desktop click exactly.

Implementation requirements:

- overlay remains click-through/hit-test transparent;
- passive global mouse observation only through supported user-space OS APIs;
- no injection/kernel hooks/graphics hooks;
- insect hit testing uses most recent physical geometry;
- body hitbox tracks visible geometry, not a large invisible radius;
- corpse state stops locomotion and trail deposition;
- click is labeled interaction abstraction, not measured physical contact.

Species-specific smear/material details remain conservative unless referenced.

Native acceptance must test clicks, double-clicks, drags, scrolling and keyboard focus on an instrumented app underneath the overlay.

## Task 36 — Keep optional screen-reactive field experimental and nonsemantic

If the existing R5 experimental sensory field is implemented, preserve these constraints:

- disabled by default;
- explicit permission;
- local processing only;
- no OCR/object recognition/semantic app detection;
- no screenshot persistence;
- low-resolution feature field shared per display;
- bounded update rate/memory;
- overlay exclusion where supported;
- automatic fallback if performance budget is exceeded.

Do not map dark pixels to hiding, food or host cues for the R8.2 arthropods without evidence.

---

# 14. Renderer, batching and LOD

## Task 37 — Generalize batching without flattening species geometry

Keep GPU instancing/batching.

Prefer:

- one/few pipelines per compatible geometry/material family;
- compact instance data with species/form tags;
- no per-creature draw calls;
- no per-creature GPU resource allocation;
- stable deterministic LOD selection;
- physical mm conversion at display boundary.

Species need distinct rigs, but that does not require distinct expensive rendering architecture.

## Task 38 — Tiny-creature antialiasing/LOD policy

At ~1 mm body length, silhouette stability is critical.

For Liposcelis especially:

- avoid subpixel flicker;
- preserve coverage without scale inflation;
- reduce articulated detail gracefully at low pixel footprints;
- do not let LOD change physical body bounds;
- test movement across display boundaries with different PPI.

For cockroach/beetle:

- antenna/leg lines must not create huge perceived footprint from over-thick rasterization;
- maintain consistent physical line width logic;
- avoid visually fattening appendages at low resolution.

---

# 15. Settings / UX

## Task 39 — Keep the settings ergonomic, not a biology cockpit

Normal users should see presets and a few understandable controls.

Suggested high-level presets:

- **Barely There** — extremely sparse;
- **Slow Burn** — default long-form infestation;
- **Established** — denser but biologically paced;
- **Custom** — exposes advanced population choices;
- optional **Interactive/Prank** — unlocks cursor/squish abstractions.

Do not expose 30 scientific constants as sliders.

Advanced/developer diagnostics may show:

- species counts;
- source/profile version;
- calibration/PPI;
- simulation performance;
- active evidence gates.

## Task 40 — Species selection defaults

Default Realistic multi-species pool after R8.2:

- Argentine ant — enabled according to existing product behavior, including its existing worker/callow/queen/male/brood system where configured;
- Liposcelis — low-probability/sparse initially;
- German cockroach first instar — rare;
- sawtoothed grain beetle — rare/sparse;
- house pseudoscorpion — **optional and normally absent**; enable through a clearly named Predator/Ecology option or custom species selection.

Do not make every species appear immediately. Multi-species infestation should emerge over time and remain configurable. The pseudoscorpion is a rare ecological event, not a default density contributor.

Legacy species not qualified by R8 stay disabled or clearly experimental.

---

# 16. macOS Spaces, menu/tray and native utility behavior

## Task 41 — Preserve/finish R5 macOS Space-transition work

If current code already fixed Space switching, verify rather than rewrite.

Instrument only if needed:

- window visibility/order;
- collection behaviors;
- active Space/screen events exposed by supported APIs;
- surface/device state;
- first successful presentation after transition;
- panic-hide state.

Acceptance:

- insects do not disappear permanently after a Space swipe;
- no duplicate overlays appear;
- no transient activation/focus stealing;
- overlay never becomes clickable to recover from a transition;
- multi-monitor topology remains correct.

## Task 42 — Keep utility UI minimal

Preserve polished monochrome menu/tray behavior if already present.

Menu should expose essentials only:

- Show/Hide;
- Pause/Resume;
- preset;
- Settings;
- Launch at Login;
- Quit;
- optional lightweight status/count.

Do not add a cluttered species-debug interface to the normal menu.

---

# 17. Documentation overhaul

## Task 43 — Update the root README for R8.2

README must explain:

- what the app does;
- the physically calibrated 1:1 illusion;
- current species and exact life stages;
- Slow Burn arrival/recruitment model;
- Argentine-ant variation and brood boundaries;
- why booklice, first-instar German cockroaches and sawtoothed grain beetles were chosen as baseline crawlers;
- why the adult house pseudoscorpion is optional/rare, its larger 7–9 mm visual span, and the difference between direct prey evidence and engineering predation abstractions;
- why Drosophila and house centipedes are not in the current Realistic species set;
- click-through/input safety;
- optional interaction modes;
- privacy/no-semantic-screen-analysis policy;
- performance expectations;
- supported platforms;
- build/run instructions;
- scientific evidence/provenance model;
- known evidence gaps.

## Task 44 — Add/update supporting technical docs

At minimum:

- `docs/R8_SPECIES_EVIDENCE.md`;
- `docs/R8_REALISTIC_MODE_GATES.md`;
- `docs/PHYSICAL_SCALE_AND_DISPLAY_PPI.md`;
- `docs/POPULATION_MECHANISMS.md`;
- `docs/PRIVACY_AND_SCREEN_REACTION.md`;
- `docs/PERFORMANCE_R8.md`;
- `docs/PROVENANCE_R8.md`.

Historical R3 and R5 docs remain available and clearly labeled historical where superseded.

---

# 18. Biological validation suite

## Task 45 — Add parameter/provenance tests

For every enabled species/form, tests must verify:

- species name and target stage;
- numeric units;
- evidence class;
- source ID resolution;
- runtime permission;
- no blocked parameter silently consumed;
- no different-stage/cross-species evidence promoted to direct.

## Task 46 — Add distribution diagnostics

Use deterministic seeded simulations and compare distributions, not single frames.

### Ant
Preserve existing speed/trail/neighbor diagnostics.

### Liposcelis
Until full tracks exist, validate only what is honestly specified:

- physical size;
- engineering motion bounds relative to measured assay anchor;
- no invented direct turn/pause claims;
- arrival schedule behavior.

### Blattella first instar
Validate:

- speed by zone;
- stop hazards/durations;
- edge occupancy;
- exit hazard;
- departure angles;
- mean free path.

### Oryzaephilus
Validate:

- speed anchors by configured substrate prior;
- walking/stopping time envelope;
- stop counts;
- climb attempt timing;
- physical scale;
- no flight.

### Chelifer cancroides adult (optional)
Validate:

- body-length and pedipalp-length distributions stay within the audited profile;
- total extended visual envelope is not silently compressed;
- forward/backward gait phase and leg-pair coordination use the direct adult study;
- microstops are present in the correct behavioral context;
- whole-body roaming speed is not falsely labeled as a complete measured natural distribution;
- predator interactions preserve evidence class per prey;
- *L. entomophila* evidence is not mislabeled as direct *L. bostrychophila* evidence;
- generic ant-prey evidence is not mislabeled as Argentine-ant-specific calibration;
- normal population density remains near zero/one and development is not time-compressed.

## Task 47 — Add physical screenshots/measurement harness

Create developer-only test captures/rulers that let implementation reviewers verify:

- one reference organism over a calibrated mm grid;
- expected px dimensions for each test PPI;
- low-PPI silhouette stability;
- high-PPI detail scaling;
- cross-display transitions.
The harness itself must not alter runtime scale.

---

# 19. Performance/resource gates

## Task 48 — Re-profile every new species in isolation

Required standalone workloads for each new species:

- 100;
- 500;
- 1000 individuals where biologically/renderer-wise meaningful;
- a higher stress run such as 2000–5000 to find nonlinear failures, not as a release density promise.

Measure:

- simulation CPU time;
- render CPU time;
- GPU completion/frame time where available;
- allocations/frame and allocations/s;
- working-set memory;
- instance-buffer growth;
- spatial-index cost;
- population-scheduler cost.

## Task 49 — Mixed-population release workload

Run a representative mixed scene containing ants + all three baseline R8.2 secondary crawlers.

At minimum compare 100/500/1000 total creatures with realistic baseline composition and a worst-case geometry mix. Separately stress 20/100 pseudoscorpions only as non-biological renderer/interaction stress tests.

R5 target retained unless stricter baseline exists:

- **p99 frame time ≤ 16.67 ms at the 1000-creature release workload** on the reference machine;
- no severe stutter from spawn/despawn bursts;
- no unbounded per-frame allocations;
- no one species' renderer/controller causing nonlinear collapse.

If performance misses, optimize the responsible subsystem rather than lowering biological update correctness or physical-size fidelity.

---

# 20. Native safety and endurance

## Task 50 — macOS native acceptance

On the actual host where possible, test:

- startup/quit repeatedly;
- hide/show;
- pause/resume;
- menu/tray actions;
- multiple monitors;
- PPI/display-boundary movement;
- Space swipes;
- sleep/wake;
- display connect/disconnect if practical;
- graphics device/surface recovery paths;
- click-through/input probe;
- panic hide;
- optional global click observation permission path;
- optional screen-capture permission path if experimental screen reaction remains.

Fail closed if transparent/nonactivating/click-through state cannot be proven.

## Task 51 — Windows preservation

Do not regress the Windows tray/overlay path while focusing on macOS.

Run available automated tests/builds and document any native checks unavailable on the current host.

## Task 52 — Endurance/soak

Run at least:

- 2 h representative Slow Burn/mixed-species soak;
- repeated hide/show and preset changes;
- population increase/decrease cycles;
- pause/resume;
- settings save/reload;
- renderer/device recovery if test hooks exist.

Watch for:

- memory growth;
- stale instance buffers;
- population IDs leaking;
- trail/spatial fields growing without bound;
- native window duplication;
- scheduling drift;
- persistence-history corruption.

---

# 21. Packaging and provenance

## Task 53 — Keep research out of the shipping runtime

Do not package the R8/R8.1 research bundle, ~1 GB reference-media corpus, giant ant Mega Pack, Dryad archives or image-reference collections into the normal app. Those belong to the implementation/research handoff, not the shipped utility.

Compile only the validated compact profiles/runtime data required by the app.

For each compiled profile record:

- source manifest version/hash;
- species/stage;
- source IDs;
- evidence classes;
- compiler version;
- profile SHA-256.

## Task 54 — Asset licensing audit

Before shipping any copied visual reference or derivative asset:

- verify the exact source page;
- verify author/credit;
- verify license and derivative/redistribution terms;
- store required attribution;
- compute local SHA-256.

Reference-only images may guide independently created geometry but are not silently redistributed.

---

# 22. Final biological audit

## Task 55 — Re-audit every enabled Realistic species/form

For each enabled form produce a final table:

- species;
- exact stage/form;
- body scale source;
- appendage envelope source/status;
- body locomotion source/status;
- gait source/status;
- flight/jump status;
- population mechanisms;
- cursor response class;
- click/squish class;
- screen-response class;
- all donor/engineering assumptions;
- unresolved gaps.

Release is blocked if:

- a missing value has been invented and labeled direct;
- a different life stage has been silently transferred;
- flight is suppressed for a species whose ordinary exposed behavior requires it without disclosure;
- scale is inflated in Realistic;
- ant biology regresses;
- deferred candidates have leaked into Realistic.

---

# 23. Final verification and handoff

## Task 56 — Completion is based on executed verification, not source existence

Run and record:

- full automated test suite;
- profile/compiler validation;
- biological distribution diagnostics;
- physical/PPI validation;
- standalone species benchmarks;
- mixed benchmarks;
- native safety/input tests;
- macOS Space tests;
- endurance/soak;
- settings migration tests;
- packaging/build checks;
- license/provenance audit.

## Task 57 — Produce final local artifacts

Deliver at minimum:

- updated source tree;
- `APP_EXECUTION_R8_2.md`;
- final test report;
- benchmark report/raw benchmark outputs;
- biological validation report;
- R8.2 enabled-species/gate report;
- native acceptance report;
- packaging artifacts if the environment permits;
- `SHA256SUMS.txt` for final deliverables;
- explicit blocker list for anything that genuinely could not be tested.

Do not claim a native test passed if the host/permission/hardware was unavailable.

---

# 24. Definition of done

R8.2 implementation is complete only when all of the following are true:

1. The newest cleaned app remains the base; no older checkpoint was restored over it.
2. Existing overlay safety, click-through, panic hide, physical scaling and recovery policy are preserved.
3. Argentine-ant behavior/variation/brood work is preserved without fake castes or brood locomotion.
4. Slow Burn starts near empty and escalates by explicit arrivals/recruitment/development mechanisms.
5. *Liposcelis bostrychophila* adult is implemented at 1:1 profile-selected physical scale with strain/form evidence limits visible.
6. ~24 h first-instar *Blattella germanica* uses the R3 exact-stage bounded locomotion model.
7. Adult *Oryzaephilus surinamensis* uses direct adult speed/state evidence and has no routine flight state under the current Purdue “no record of flight” boundary; documentation does not call it physiologically flightless.
8. Adult *Chelifer cancroides* is available only as an **optional rare predator/ecology species**, rendered at honest body + pedipalp scale with direct adult gait evidence.
9. *Chelifer* predation provenance distinguishes direct prey (*Varroa*, *L. entomophila*), same-genus transfer to *L. bostrychophila*, general ant-prey evidence, and any engineering interaction constants.
10. None of the non-ant species is an ant reskin.
11. Body locomotion qualification is separate from articulated gait qualification.
12. Drosophila, bed bug, Tribolium and still-deferred candidates are not silently enabled in strict Realistic.
13. Realistic mode does not enlarge organisms for visibility.
14. Cursor/click/screen mappings are explicit biological analogs or labeled engineering/experimental abstractions.
15. Settings remain simple and ergonomic.
16. README/docs describe the actual R8.2 biology, limitations, privacy and provenance.
17. 100/500/1000 mixed performance gates are re-measured and the release workload meets the verified frame-time target.
18. Native click-through/input behavior is verified and never weakened by squish interaction.
19. MacOS Space switching does not break or duplicate overlays.
20. Long-run memory/state behavior is stable.
21. Every shipping scientific parameter/profile is traceable to source/evidence class.
22. Remaining evidence gaps are documented as gates, not papered over with constants.

---


# 25. Final response expected from the implementation agent

The implementation agent's final response should be concise but concrete:

- what changed;
- which R8.2 species/forms are now implemented;
- any Realistic-mode gates still intentionally closed;
- test/benchmark/native results;
- any genuine blockers;
- links/paths to final source/build/report artifacts.

Do not narrate hours of work or claim future background work. Report what was actually completed and verified.