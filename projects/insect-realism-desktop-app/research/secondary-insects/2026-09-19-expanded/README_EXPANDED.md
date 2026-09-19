# Secondary insects research expansion R2

This continues, rather than replaces, the research preserved at
`feat/insect-secondary-research-20260919@a081960d570d092ee6c1576d5e503e1f42e60e96`.

Application runtime code is unchanged by this research branch.

## Verified expansion

- Open empirical payload: **509,971,606 bytes**.
- GitHub Actions run: `35423878989`.
- Artifact: `10578147657` / `insect-secondary-empirical-data-r2`.
- GitHub artifact ZIP digest: `sha256:e01abb275ea8888316f33b685a8d486d860409cc76090dcb1dc06b9787bcfef3`.
- Selected raw files: five Drosophila walking/navigation behavior files from public Zenodo records.
- Original implementation parameter ledger: 92 rows.
- Added population-morphology ledger: 57 rows.
- Expanded local source manifest: 68 records.
- Expanded local dataset manifest: 26 records.

The raw byte count is not a quality score. Drosophila dominates bytes because it has
unusually large public quantitative motion corpora. Smaller cockroach, bed-bug and
beetle datasets remain scientifically valuable and are indexed rather than padded
with unrelated genomics or microscopy.

## Population realism change

A species is no longer treated as one adult rig plus random scale. The research now
separates:

1. stable individual variation;
2. sex/caste/morph;
3. developmental stage/instar;
4. maturation or feeding state;
5. ecological context / surface-visibility prior.

The current ant is *Linepithema humile*. It remains monomorphic/unimodal at the
worker-caste level, so it gets continuous correlated worker variation rather than
invented majors/soldiers. Queen, male, callow and brood are separate forms.

Future truly polymorphic ant profiles are researched separately, including
*Solenopsis invicta*, *Atta cephalotes* and a concrete future *Pheidole* species.
