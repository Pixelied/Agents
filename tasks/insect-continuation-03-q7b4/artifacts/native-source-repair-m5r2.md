# Native source repair continuation

This is continuation of `feat/insect-continuation-03-q7b4`, not a new project or a source restart.

The source-recovery workflow `.github/workflows/insect-native-source-repair-m5r2.yml` is confined to the existing `ci-insect-continuation-native` component owned by `astra-insect-resume-m5r2`. Its only source writes are the original checkpoint Cargo.lock, three byte-verified biology assets, and the four already regression-tested settings files in the same agent's leased project root.

Before any source preparation or source publication, the workflow reads the current main-branch lease, verifies the owner/state and checks the actual UTC expiry. It refuses to overwrite a different existing lockfile or profile asset. The complete lockfile SHA-256 remains the authority; it restores the original cc 1.4.6 package record only after checksum hydration, never an unreviewed dependency upgrade.

Native build jobs consume the exact source commit returned by that preparation job. Native compilation and domain/platform tests do not qualify physical input pass-through, real monitor calibration, representative hardware frame rate, signing trust, or a finished installer. Those gates remain separate.

Previously published sources, other project roots, and the separate research lineages are not reset or deleted. No force push is used.
