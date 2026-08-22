# CrystalBot V3 Baseline

- Base branch: feat/crystal-optimizer-v2-lethal-efficiency
- Base commit: ca1db1d97a0f1c0ecb148efee7298dca2139743e
- Minecraft: 26.1.2
- Java: 25
- Mod id: crystaloptimizer
- Mod version: 0.2.1
- Fabric Loader: 0.19.3
- Fabric API: 0.155.2+26.1.2
- Loom: 1.17-SNAPSHOT
- Mod Menu: 18.0.0-beta.1
- Existing project gate: PASS (PR #24, Crystal Anchor Optimizer 26.1.2 CI run 474)
- Workspace unit tests: PASS via pull-request validation workflow
- Workspace validation: PASS via pull-request validation workflow run 1322
- Verification note: this execution environment cannot clone github.com directly, so GitHub Actions is the authoritative Java 25/Gradle verification surface for this implementation branch.
- Known V3 audit gaps: candidate starvation, 3-target cutoff, duplicate map build,
  unwired target/inventory events, unused live prediction, zero local absorption,
  hurt-window double application, continuation reconciliation gap.
