# Installation and everyday controls

## Before installing

This is a development candidate. Only install a package built from the exact reviewed source and inspect its accompanying verification and checksum reports. No native installer or successful native launch was produced in the Linux release-audit environment. Do not treat a source ZIP, a synthetic packaging test fixture, or a cross-target `cargo check` as an installable app.

Normal operation requests no screen-recording or Accessibility permission, installs no service or driver, and does not need administrative access. The app never injects into another process. A graphical driver or OS incompatibility must leave overlays hidden rather than work around OS protection.

## macOS

Build on macOS using the release instructions. Open the generated DMG, drag **Insect Realism.app** to `/Applications` or a stable user Applications directory, then launch that `.app`. The application is a menu-bar accessory, not a normal Dock window. If the OS blocks an unsigned development build, review the source/signature first and use the normal macOS user-approval flow; do not disable Gatekeeper globally.

The default local package is ad-hoc signed, not Developer-ID signed or notarized. A configured signing identity and notarization profile are optional build inputs, never included in a checkpoint. Enable Launch at Login only after installation in a stable location. Translocated or unbundled paths are rejected by the native adapter.

To uninstall, disable Launch at Login in the app, quit, and remove its `.app`. The owned login item is `~/Library/LaunchAgents/com.pixelied.insect-realism.plist`; a manual cleanup may remove that exact file, never the entire LaunchAgents directory. User settings are deliberately separate and may be retained.

## Windows x64

Build the GUI-subsystem executable and per-user MSI on a Windows machine with the required SDK. The MSI installs under the current user's Local AppData `Pixelied\Insect Realism` directory and creates a Start Menu shortcut. A portable ZIP is also generated. Extract portable builds to a stable location before enabling startup; moving the executable changes its startup path.

Uninstall through Installed Apps. The MSI helper removes only the current user's `InsectRealism` value under `Software\Microsoft\Windows\CurrentVersion\Run`. It does not delete the shared Run key. Installed files and empty application/shortcut directories are removed; user-added files and settings are preserved. No native installation/uninstallation run has yet been verified for this snapshot.

## Controls

The menu/tray provides Hide All / Show Creatures, Pause / Resume, Settings, presets, Launch at Login and Quit. The default panic binding is **Ctrl+Alt+Shift+H**; on a Mac, Alt is Option. Registration failure keeps overlays hidden and reports the conflict. Choose another supported binding in Settings.

**Hide All** is immediate and temporary: it hides every overlay and freezes biology for the current session. **Pause** is persisted. **Quit** exits. Closing Settings does not quit or pause the ants. Safe Overlay Mode and per-application exclusions may intentionally hide the overlays.

If an overlay ever intercepts input, invoke panic hide or quit from the menu/tray. If that fails, stop the application through the OS process manager. Record the failure as a release blocker; do not continue testing over critical work or protected/full-screen applications.

## Physical-size calibration

Only ready, enabled displays participate. Untrusted display size opens calibration instead of guessing PPI from UI scaling. Match the displayed reference width to the **85.60 mm long edge** of a standard ID-1 card or a ruler. Do not reveal or capture payment-card details. Keep the settings window entirely on the display being calibrated; save per monitor and pixel mode. Repeat after a mode/rotation change or when the saved identity no longer matches.

The numerical transform is tested, but a ruler/card check on actual hardware remains required. The Advanced creature-scale multiplier intentionally enlarges or shrinks the creature; it does not change the physical monitor calibration. Normal Realistic behavior keeps cursor interaction off.

## Settings and recovery

The app uses `directories::ProjectDirs::from("studio", "Pixelied", "InsectRealism")` and a `settings.json` file under its platform config directory. Exact paths depend on OS conventions. Writes are atomic; invalid files are preserved as corrupt backups. Newer unsupported schema files are left untouched and the utility reports read-only recovery rather than silently downgrading them.

On GPU failure, affected overlays hide, the menu/tray remains available where the OS permits, and automatic reconstruction is bounded. Repeated loss exhausts that budget; explicit user retry or restart is necessary. A hung graphics initializer does not start unlimited new driver threads.
