# Empirical data mirror report

GitHub Actions run `35423878989` downloaded selected public Zenodo data, checked
minimum file sizes, verified provider MD5 values, generated SHA-256 values, and
published artifact `10578147657`.

| Source | File | Bytes | SHA-256 | Allowed use |
| --- | --- | ---: | --- | --- |
| York et al., Zenodo 7306160 / Dryad 10.5061/dryad.z8w9ghxfc | all_trials_umap_layout_annotated_30hz_size10_with_louvain_clusters_and_position.RDS | 260,045,012 | 6b452a3562bab0905a67098d4a047201e4344a40155bcbb193a84c38753b02be | cross-strain/species walking behavior-state validation; not direct leg-gait calibration |
| Siliciano et al., Zenodo 20751812 | constant_logs.zip | 162,289,820 | 76fc103e8d8537ae6a90ab1d402d1c87bef501bae84204135e4d749a4dc6e598 | cue-conditioned trajectories only |
| Siliciano et al., Zenodo 20751812 | gradient_logs.zip | 38,079,556 | 83c5aa3388cb2db4998b130396c2629cdb987f6904e8d88843764d476f23a575 | cue-conditioned trajectories only |
| Siliciano et al., Zenodo 20751812 | 45_logs.zip | 25,619,464 | 2d5ea1156881b498b7b54f26eb275f04af7b3cba0956a747ef4e5fbe44463eb4 | directional cue-conditioned trajectories |
| Siliciano et al., Zenodo 20751812 | 90_logs.zip | 23,937,754 | 2549cbb147a5546705089639bef070bfc849a615c18af7fda2162aa3ef929a70 | directional cue-conditioned trajectories |

Total selected empirical bytes: **509,971,606**.

The first Dryad direct-download attempt returned small access/challenge documents.
Minimum-size validation rejected those responses, so zero challenge-page bytes were
counted. Public Zenodo mirrors were used for the successful artifact.

Important boundary: odor/cue-conditioned trajectories are never evidence that the
desktop app should infer odors, hosts, windows or screen semantics from pixels.
