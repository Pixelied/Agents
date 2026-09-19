# InsectRealism R8.2 — Quad audit report

**Result: PASS WITH CORRECTIONS APPLIED**

This audit was performed before creating the consolidated release. It checks four independent layers: scientific claims, evidence/directness, data-package integrity, and implementation-plan consistency.

## Check 1 — Scientific/taxonomic accuracy

PASS after corrections.

Corrections applied:
1. **Liposcelis bostrychophila is not universally female-only.** Parthenogenetic domestic strains are well documented, but sexual strains also exist. The current plan uses a strain/form-aware adult profile instead of a species-wide female-only claim. Source: Yang et al. 2015, DOI 10.1038/srep10429.
2. **Oryzaephilus surinamensis is not called physiologically flightless.** Purdue reports developed wings but “no record” of flight for the species. Current Strict Realistic therefore omits routine flight under that source boundary without making a stronger biological claim.
3. **Chelifer cancroides is an arachnid/pseudoscorpion, not an insect.** Its direct 2022 locomotion study supports adult body/leg/pedipalp measurements, 500-fps gait analysis, 100–200 ms microstops, alternating-tetrapod forward/backward coordination, and fast backward escape. DOI 10.1242/jeb.243930.
4. **Chelifer 7–9 mm is total extended pedipalp span, not body length.** Penn State reports adult body around 3–4 mm and 7–9 mm across when pedipalps are extended.
5. **Chelifer prey evidence is now classified correctly.** The 2026 direct study tested Varroa destructor and Liposcelis entomophila. Our rendered booklouse is L. bostrychophila, so this is a same-genus prey transfer. Penn State lists ants among broader prey, but no Argentine-ant-specific quantitative calibration was located.\n6. **The suspicious 3.88 mm/s Chelifer activity value was independently rechecked rather than trusted.** The paper text reports approximately 14 m h^-1 during the 1 h arena observation; converting that value gives about 3.89 mm/s. The final extract now uses this transparent conversion and paper-visible 61% mobile, 0.16 ± 0.04 s microstop, and forward/backward/upside-down maxima instead of relying on harder-to-audit supplementary-only summary fields.

## Check 2 — Evidence/directness discipline

PASS after corrections.

- Argentine-ant evidence remains separate and preserved; R8.2 does not restart or reduce the ant system.
- First-instar German-cockroach body locomotion remains exact-stage evidence; adult gait references are not promoted to nymph gait.
- Liposcelis assay speed remains an assay-conditioned anchor, not a fabricated natural distribution.
- Oryzaephilus surface-conditioned means remain separate; they are not averaged into a fake universal speed.
- Chelifer direct adult gait is qualified, while natural free-roaming speed distribution and target-prey attack constants remain partial/gated.
- Large GBIF-derived reference imagery is explicitly demoted from numerical-calibration authority.

## Check 3 — Data integrity / release corpus

PASS for the collected corpus.

R8.1 acquired four license-filtered exact-species reference corpora:

| Species | Validated files | Raw bytes |
|---|---:|---:|
| Blattella germanica | 383 | 441,436,941 |
| Liposcelis bostrychophila | 322 | 87,095,985 |
| Oryzaephilus surinamensis | 333 | 263,465,786 |
| Chelifer cancroides | 340 | 255,196,459 |
| **Total** | **1,378** | **1,047,195,171** |

The acquisition pipeline validates image decodability/size, filters redistribution-incompatible licenses, hashes files, records original URLs and provenance, and keeps unclear-rights publisher material link-only.

Important limitation: this ~1 GB is **mostly visual/reference media**, not 1 GB of locomotion trajectories. The high-value numerical evidence is much smaller and is included separately as normalized extracts/source manifests. This distinction is now explicit throughout the current plan.

## Check 4 — Implementation-authority consistency/completeness

PASS after rewrite.

Problems found and fixed:
- old R8 plan rejected Chelifer while the new decision includes it;
- old R8 foundation prompt said “exactly three” secondary targets;
- old plan contained a duplicated Liposcelis section header;
- insertion of the predator wave initially caused section/task-number collisions; corrected;
- ant preservation is now repeated in mission, authority, validation and definition-of-done language;
- the plan now includes Chelifer physical scale, gait, predation evidence classes, population timing, UI default, documentation, validation and performance gates;
- the plan explicitly states that current verified app/debugging fixes are preserved and that only older species-direction documents are superseded.

## What the implementation agent should treat as authoritative

1. `00_START_HERE/ASTRA_READ_THIS_FIRST.md`
2. `00_START_HERE/CURRENT_IMPLEMENTATION_PLAN_R8_2.md` — **single implementation authority**
3. current R8.2 species decision/gates/profiles/manifests
4. current cleaned app source and previously completed verified fixes
5. historical R3/R5/R7/R8 docs only for provenance where the current plan does not supersede them

The implementation agent should **not juggle the old R8 plan plus a direction-change note**. R8.2 incorporates the direction change directly into the plan.

## Remaining legitimate gaps

The package is implementation-ready for the evidence-backed parts, but it is not scientifically complete in areas where no source was found. The main remaining gaps are exact-stage articulated cockroach gait, richer booklouse trajectories/turn-pause data, adult grain-beetle gait/raw XY, Chelifer target-prey attack kinematics and Argentine-ant-specific predation calibration, plus display-glass traction for all species. These stay as explicit gates.
