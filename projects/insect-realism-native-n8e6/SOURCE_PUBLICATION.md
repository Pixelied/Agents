# Verified source publication

Status: in progress, not an application release.

Continuation branch: `feat/insect-native-verified-20260918-a6d2`.
Dependency/base: `feat/insect-native-build-20260918-n8e6` at `05a939593809344b8825c7caaf5e2ec1eb44a057`.
Source: the user-provided Insect_Desktop_Continuation_02_Source.zip; the original verified application is preserved, not replaced with the incomplete older GitHub copy.

The existing native-source release has no attached ZIP. A fresh agent acquired the released project and CI scopes on main and is publishing normal source files, with authenticated reconstruction of the original large data assets.

Locally verified this session:
- Original tooling baseline: 83 passed, 1 skipped (Cargo unavailable), with the supplied Mega Pack configured.
- New reconstruction regressions: 4 failed before implementation, then all 4 passed.
- All 107,920 normalized trajectory rows were reproduced byte-for-byte from the original coordinate CSV.
- All 20 leader tracks, 53,960 observations, and every input metadata field matched the original audited snapshot.

Pinned normalized trajectory SHA-256: `383c51640960ed52b880470fbd4436da0354400430ea2ce78839810ae51fbe97`.
Pinned final snapshot SHA-256: `b5cc912378a92c8b9f838047c7a2fef6d7a115c1b9520a4c350d477fea319375`.
Pinned runtime bundle SHA-256: `d58754a7064a10bc87cf415462dfe1ee5a48b528962fae3852ee0d3dd1dba07e`.

Native application compilation, installers, interactive input safety, representative-hardware performance, and physical display acceptance are not yet verified by this publication checkpoint. No claim of full release completion is made.
