# InsectRealism R8.1 — large secondary-arthropod data pack

This directory builds the large, auditable research corpus for the **new non-ant arthropods only**.

It does **not** replace or narrow the existing `Linepithema humile` system. All existing ant work remains in scope: realistic worker-size variation, callows, queens, males, eggs, larvae, pupae/brood, colony context, recruitment, trails/pheromone work, and ant-specific interaction evidence.

## Revised non-ant set

- `Liposcelis bostrychophila` — adult booklouse.
- `Blattella germanica` — ~24 h first-instar nymph target.
- `Oryzaephilus surinamensis` — adult sawtoothed grain beetle.
- `Chelifer cancroides` — **optional rare pseudoscorpion predator**, not an insect.

`Chelifer cancroides` is deliberately optional because its ~3 mm body carries long pedipalps and can span roughly 7–9 mm when extended. Its inclusion is justified by exceptionally good exact-species locomotion evidence and genuine indoor predatory ecology, not by pretending it is as visually small as a booklouse.

## What the workflow collects

The workflow downloads a large exact-species visual-reference corpus from GBIF-backed media while enforcing redistributable licenses (CC0 / CC BY / CC BY-SA), exact-species resolution, minimum image size, byte-level deduplication, and SHA-256 provenance.

The default quotas are approximately:

- booklouse: 180 MiB;
- German cockroach: 420 MiB;
- sawtoothed grain beetle: 300 MiB;
- house pseudoscorpion: 300 MiB.

That is a **maximum target of ~1.17 GiB** before manifests. The script never substitutes unrelated genomes or another species merely to hit the target; if reusable exact-species material is scarcer, the final report says so.

High-value publisher material whose redistribution rights are unclear stays in `link_only_references.csv` rather than being smuggled into the bundle.

## Downloading the finished data

GitHub Actions publishes five downloadable artifacts for each successful run: one per target species plus a manifests/Astra-handoff artifact. This avoids trying to push ~1 GB of binary data through Git history or ChatGPT attachments.
