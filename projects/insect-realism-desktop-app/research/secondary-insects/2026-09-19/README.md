# Secondary insects research — 2026-09-19

Research-only continuation from the verified insect app checkpoint.

- Base application/evidence commit: `515c6c37c20ed25a6e009ba831b91cb30c9521ae`
- Full local research artifact SHA-256: `0ea7f8d9a56702fde43128a6defa7d2620f46b23a8ae7c3560452c4b1d1a01b5`
- Research corpus in the delivered artifact: 38 source records, 16 dataset records, 92 implementation-facing parameter rows.
- Runtime/source code changed by this branch: **none**.

## Selected implementation waves

1. **Adult fruit fly — Drosophila melanogaster** — Wave 1.
2. **First-instar German cockroach — Blattella germanica** — Wave 1.
3. **Adult bed bug — Cimex lectularius** — Wave 2 after direct adult gait extraction.
4. **Adult red flour beetle — Tribolium castaneum** — Wave 3 only after direct adult gait evidence.

The number of enabled species is evidence-gated. Shipping two or three high-fidelity species is preferred to forcing four by borrowing unsupported biology.

## Evidence rules

- Target-species + target-life-stage measurements are direct evidence.
- Different-life-stage/cross-species data is labelled and cannot silently become calibration.
- Whole-body trajectory evidence does not prove leg gait.
- Host odor, heat, tactile contact, or air movement are not equivalent to desktop pixels/cursor motion.
- Missing numerical biology stays missing.
- Reference images/papers are not shipping assets without a separate reuse audit.

## Reserve candidates

- **Anthrenus verbasci**: good ~3 mm scale, insufficient neutral gait/trajectory corpus.
- **Lepisma saccharinum**: visually strong, but adults are usually 7–10 mm and the quantitative gait corpus is weak for the current 2–4 mm phase.

See the adjacent plan and evidence notes for implementation details and release gates.
