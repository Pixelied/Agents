# Native development packages and release policy

These recipes build real native artifacts. A successful package is still a
development candidate until the manual acceptance matrix is recorded. Current
run identities and checksums belong in `CONTINUATION_STATUS.md`.

## macOS

Run `bash app/packaging/macos/build-dmg.sh` on macOS with Xcode Command Line Tools,
Rust's target standard library and Python 3.11+. `TARGET` defaults to
`aarch64-apple-darwin`; `x86_64-apple-darwin` is an optional separate thin target.
Universal 2 is not built by this recipe. Minimum deployment target defaults to 13.0.
`OUTPUT_DIR` may select an output directory; the default is under `dist/`.

The script stages the real Mach-O executable as `Contents/MacOS/InsectRealism`,
LSUIElement metadata, the original ICNS icon, runtime profiles/presets/shader and
license notices. It verifies package content, signs, and verifies the resulting
signature. It produces a DMG and a `.app.zip`, then hashes their final bytes.

`MACOS_SIGN_IDENTITY` defaults to `-` (ad-hoc). An existing Developer ID identity
can enable hardened-runtime signing. `NOTARY_KEYCHAIN_PROFILE` requests notarization
using an already-provisioned `notarytool` keychain profile; it requires a real
Developer ID identity. The script notarizes/staples the DMG, not a separate notarization
of the `.app.zip`. No entitlement or credential is invented or bundled. Ad-hoc signing
is suitable for development, not a claim of notarization or broad Gatekeeper trust.
The outer image inventory is written after signing, outside the sealed `.app`.

## Windows x64

Run `pwsh -File app/packaging/windows/build-msi.ps1` on Windows with MSVC and the
Windows SDK, Rust, Python 3.11+, and a .NET SDK capable of running the pinned WiX
4.0.6 tool (.NET 6 is provisioned by the provided workflow). `-OutputDirectory`
selects output; the default is under `dist/`.

The script restores `app/packaging/windows/.config/dotnet-tools.json`, builds the GUI
PE32+ executable, stages real assets and audited licenses, generates deterministic
WiX components and creates a per-user MSI plus portable ZIP. Original ICO artwork is
included in Resources and referenced by the installer/Start Menu. This does not claim
an embedded custom PE executable icon. The MSI cleanup helper removes only its owned
startup value. User-added files and unrelated registry entries are not deleted.

`WINDOWS_CERT_SHA1`, when supplied, identifies an existing signing certificate.
The executable and MSI are signed and verified by signtool; no private key/PFX is
copied. With no certificate, the output is an unsigned development package.
Run actual install/uninstall, launch-at-login, input and recovery acceptance before
any release, regardless of whether the packaging command succeeded.

## Workflow ownership

The active repository-level `.github/workflows/insect-packages-m5r2.yml` builds
this continued project on macOS and Windows and preserves source/evidence/artifacts.
Both native jobs must use the same tested source revision. Native result logs and
exact profile identity are inspected before accepting a build.

The repository-level `.github/workflows/insect-release.yml` is tag/manual-only:
`insect-desktop-v*` tags must match the app version. It runs Linux offscreen
verification plus native packaging and creates a DRAFT prerelease only after all
jobs succeed. Manual dispatch can request the two-hour software-GPU soak. There
is no duplicate ordinary branch-push trigger; everyday branch builds use the
package workflow. The project-local file remains the reusable standalone template.

The tag/draft path is configured, not a claim that a release tag was published
or that native manual acceptance passed. No canonical-branch merge or public
production release occurs automatically.

## Integrity and updates

The profile compiler now emits version `2026.09.17-donor-transfer.2` using pinned
software math. The raw input is unchanged; exact profile/report byte comparisons
remain mandatory. Old `.1` measurements must retain their historical label.

`python scripts/make_checkpoint.py /outside/project/checkpoint.zip` writes an
atomic reproducible source archive with verified checksums. It excludes Git,
machine config, build/vendor/cache directories, fonts and nested archives.
Package manifests verify payload bytes independently from signing/notarization.
Do not copy credentials or private signing material into the project or artifacts.
