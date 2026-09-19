# R3 Quad Audit Report

Date: 2026-09-19

## Result

R3 is a corrected conservative audit, not a claim that scientific literature can be proven mathematically perfect. If a value/source could not be verified to the required species, life stage, and assay scope, R3 downgrades it to context-only/secondary-historical evidence or blocks it from release calibration.

## Four audit layers

1. File/data integrity: JSON/CSV parsing, ZIP integrity, byte counts, SHA-256, and provider MD5.
2. Source identity: DOI/title/species/life-stage checks for calibration-relevant sources plus suspicious/context sources. Duplicate records receive canonical IDs.
3. Parameter provenance: implementation parameters carry evidence class, life-stage match, experimental context, runtime permission, and audit note.
4. Biological transfer rules: cross-stage, cross-species, and conditional-assay values cannot silently become baseline biology.

## Major corrections

- Corrected Linepithema humile brood: 0.62/1.18/1.59 mm are male-larva mean lengths without the dorsal protuberance, not worker brood. Worker larvae with the dorsal protuberance are 0.57/0.78/1.23 mm mean lengths.
- Downgraded bed-bug nymph dimensions copied from the 2022 Ghana paper to secondary historical C. lectularius values; that paper directly measures C. hemipterus.
- Corrected four German-cockroach source identities/uses.
- Corrected Drosophila boundary-exploration provenance and removed centrophobism/anxiety semantics.
- Scoped Drosophila flight statistics and bed-bug adult sizes to their actual experimental contexts.
- Removed an unsupported Tribolium 3-4 mm population range and separated motion-metric provenance.
- Removed pseudo-precise candidate readiness scores.
- Fixed split-delivery checksum semantics.

## Verified raw empirical companion

- ZIP bytes: 509,976,983
- ZIP SHA-256: e01abb275ea8888316f33b685a8d486d860409cc76090dcb1dc06b9787bcfef3
- Selected empirical bytes: 509,971,606
- Five empirical files: provider MD5 and locally recomputed SHA-256 matched.
- GitHub Actions source run: 35423878989
- Artifact id: 10578147657

## R3 local validation

- source records: 68
- scientific source records: 66
- canonical scientific sources: 58
- dataset records: 26
- parameter rows: 95
- population-morphology rows: 69
- source-reference resolution: PASS
- known-bug assertions: PASS
- JSON parse: PASS
- CSV parse: PASS
- raw ZIP integrity: PASS
- docs ZIP SHA-256: 57463eb2fd574ac8f0564f4f05ceb7353d35c27e99d9e219ff74273299d36d19

## Release-calibration policy

A source marked not-release-calibrating, duplicate alias, project context, secondary historical, cross-stage, or conditional-assay cannot silently populate a Realistic baseline distribution.
