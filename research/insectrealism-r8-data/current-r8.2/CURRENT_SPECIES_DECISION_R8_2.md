# InsectRealism R8.2 — Current species decision

**Authority:** current. This replaces the R8 species-selection conclusion for implementation purposes.

The existing **Linepithema humile (Argentine ant)** system remains the core social species and is not reduced by this change. Preserve the already researched/implemented worker size and morphology variation, callows, queens, males, eggs, larvae, pupae/brood, trail/pheromone behavior, recruitment and colony/population logic.

## New non-ant implementation set

### 1. Liposcelis bostrychophila — adult domestic booklouse
Baseline tiny crawler. Wingless, strongly human-associated, ~1 mm-class body and suitable for sparse-to-local infestation.

Important correction: parthenogenetic domestic strains are a strong target, but sexual strains of the species are documented. Do not describe the species globally as female-only. The runtime profile must identify which strain/form size boundary it uses.

### 2. Blattella germanica — ~24 h first instar
Baseline fast edge/periphery crawler. Exact target-stage body dimensions and bounded-space locomotion evidence remain the strongest complete body-motion package among the new crawlers.

### 3. Oryzaephilus surinamensis — adult sawtoothed grain beetle
Baseline compact flat crack/crevice crawler. Purdue reports a ~2.5 mm adult, a very flat body, developed wings, and **no record of this species flying**. R8.2 therefore keeps routine flight out of Strict Realistic, while explicitly avoiding the stronger claim that the beetle is physiologically flightless.

### 4. Chelifer cancroides — OPTIONAL adult house pseudoscorpion predator
This is an **arachnid/pseudoscorpion, not an insect**. It is not a common background crawler and should normally be absent or represented by a single individual.

Why it is now included:
- direct adult morphology and 500-fps forward/backward/upside-down gait work is unusually strong;
- adults are genuinely synanthropic/house-associated;
- real predatory ecology creates a useful cross-species layer;
- the larger 7–9 mm extended pedipalp span is acceptable when treated as a rare optional predator rather than pretending it belongs to the same visual-size class as a booklouse.

Evidence limits:
- direct 2026 predation work uses Varroa destructor and **Liposcelis entomophila**; transfer to L. bostrychophila is same-genus evidence, not direct target-prey evidence;
- extension sources include ants among pseudoscorpion prey, but no Argentine-ant-specific pursuit/kill/preference calibration was found;
- adult predation on O. surinamensis or first-instar B. germanica is not assumed.

## Still rejected/deferred

Drosophila remains out of the normal exposed-crawler set because realistic adult behavior intrinsically includes flight. Normal house centipedes and silverfish remain too large for the target illusion. Clover mites remain interesting arrival-only future candidates but are nearly dot-scale at ordinary PPI and do not establish indoors. Other deferred springtails, tiny beetles and pseudoscorpions remain evidence-gated.
