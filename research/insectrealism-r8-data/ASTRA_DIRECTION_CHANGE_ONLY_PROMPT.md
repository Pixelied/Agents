# InsectRealism — R8.1 direction change for Astra/Codex

This is a **scoped change of direction only for the additional non-ant arthropods** being added to InsectRealism.

Do **not** treat this as a new project, a reset, or permission to reopen/undo unrelated bug fixes, architecture work, macOS/native-window work, performance work, rendering fixes, safety work, settings migrations, or other implementation decisions already established in the existing Astra chat/workspace.

## 1. THE EXISTING ARGENTINE-ANT SYSTEM IS STILL FULLY IN SCOPE

The core ant remains **Linepithema humile (Argentine ant)** and all existing ant research/implementation work remains valid unless a later explicit instruction changes it.

Preserve and continue the ant system including, where already planned/researched:

- realistic mature-worker physical sizing;
- continuous worker morphology/size variation;
- callow workers;
- queens;
- males;
- eggs;
- larvae;
- pupae;
- brood / young colony stages;
- colony context;
- pheromone/trail behavior;
- recruitment;
- gradual infestation growth;
- realistic development timing;
- cursor-disturbance work and other ant-specific evidence gates.

**Do not delete, simplify, demote, or replace the ant caste/size/brood work because of this prompt.**

The R8/R8.1 change applies ONLY to which **additional non-ant arthropods** join the existing ant system.

## 2. REVISED ADDITIONAL-ARTHROPOD SET

The evidence-backed secondary set is now:

1. **Liposcelis bostrychophila** — adult domestic/parthenogenetic booklouse baseline.
2. **Blattella germanica** — approximately 24-hour first-instar nymph.
3. **Oryzaephilus surinamensis** — adult sawtoothed grain beetle.
4. **Chelifer cancroides** — OPTIONAL rare predator / advanced-ecology arthropod.

Do not re-add Drosophila merely because older research exists. Do not automatically restore bed bugs, Tribolium, silverfish, house centipedes, carpet beetles, or other previously discussed candidates.

## 3. CHELIFER CANCROIDES IS NOW AN OPTIONAL PREDATOR

Correct taxonomy: **Arachnida → Pseudoscorpiones**, not an insect.

It is intentionally not a common background crawler. It should be a rare, visually distinctive predator that can appear after other tiny arthropods are established, or be enabled explicitly by the user.

### Physical scale

Direct locomotion-study morphology (n=10 adults):

- body length: mean 3.09 mm, SD 0.47 mm, range 2.13–3.84 mm;
- L1: 1.37 ± 0.13 mm;
- L2: 1.47 ± 0.20 mm;
- L3: 1.77 ± 0.16 mm;
- L4: 2.10 ± 0.19 mm;
- pedipalp length: 3.88 ± 0.72 mm, range 2.98–5.45 mm;
- body mass: 2.48 ± 0.51 mg.

Penn State reports the extended crab-like pedipalps produce roughly a **7–9 mm full span**. That large appendage footprint is why C. cancroides is optional rather than a normal high-density crawler. Do not shrink it to fit the screen illusion and do not scale it from body length alone.

### Locomotion signature

Use the direct 2022 Journal of Experimental Biology study as primary gait authority:

- high-speed recordings at 500 fps;
- coordinated alternating tetrapod gait during forward and backward walking;
- frequent forward microstops around 100–200 ms;
- forward walking generally slower than backward escape;
- backward escape reached up to about 17 body lengths/s in the study;
- upside-down locomotion is slower and less rigidly coordinated, with more legs kept in stance;
- the second leg pair has a particularly important stability role.

Do not reuse ant gait, cockroach gait, spider gait, or generic eight-leg animation if the direct C. cancroides gait can be represented.

### Predation

Predation is a real ecological reason to include this species, but evidence directness matters:

- direct C. cancroides evidence supports predation on psocids and mites;
- a 2026 study directly tested C. cancroides against Varroa destructor and the psocid Liposcelis entomophila;
- Penn State's C. cancroides/pseudoscorpion guidance explicitly includes ants among small arthropod prey;
- however, a quantitative predation rate specifically for **Linepithema humile** has not been established in this research pass.

Therefore:

- **booklouse predation** may be enabled in Realistic mode once encounter/attack timing is calibrated from direct or clearly transferable evidence;
- **mite predation** is biologically supported even if mites are not currently rendered;
- **ant predation is allowed as a plausible prey-category interaction**, but do not invent an Argentine-ant-specific kill probability, pursuit radius, attack cadence, handling time, or preference ranking. Keep those parameters evidence-gated or explicitly marked as engineering abstractions until better evidence exists.

C. cancroides should not act like an arcade enemy that continuously seeks the nearest ant. It is a small sit/wander/encounter predator using pedipalps and venom at close range.

### Population behavior

Do not make the pseudoscorpion reproduce rapidly to increase screen population.

Evidence supports slow life history: females carry roughly 20–40 eggs; egg-to-maturity is on the order of 10–24 months with three molts; adults can live for years. For the infestation illusion, visible increases should therefore come mainly from **rare new arrivals/emergence from hidden cracks**, not eggs becoming adult predators in minutes.

Default density should be very low — commonly zero, sometimes one, rarely more — unless the user explicitly chooses a stronger predator/ecology mode.

## 4. AUTHORITY BOUNDARY

Use the new R8.1 research/data package as authority for **the additional arthropods only**.

For everything else, preserve the current cleaned app/workspace and the decisions already made in the existing Astra conversation. If an older secondary-species section conflicts with R8.1, R8.1 wins for that species-selection section only.

Do not interpret this document as an instruction to undo unrelated bug fixes or re-plan the whole app.

## 5. IMPLEMENTATION TARGET AFTER THIS RESEARCH HANDOFF

When implementation resumes, the intended biological composition is:

- Argentine ants remain the rich core social species with their full caste/size/brood system;
- booklice add nearly microscopic indoor crawling and slow local establishment;
- first-instar German cockroaches add fast wall/periphery movement and a very different silhouette;
- sawtoothed grain beetles add compact, flat, crevice-associated crawling;
- C. cancroides optionally adds a rare predator that can create believable cross-species interactions.

Biological accuracy stays above species count. No additional species should be added just to make the list larger.
