# Native input-safety acceptance - RELEASE BLOCKER

**Not executed in this Linux audit.** Programmatic style/state checks, cross-compilation, synthetic input events and screenshots do not prove real input pass-through. Use a disposable native test session and keep a reliable way to quit the utility.

Record date, exact executable SHA-256, local source commit/dirty state, OS build, CPU/GPU/driver, monitor dimensions/resolution/refresh/UI scaling, physical calibration and package/signature status. Record each case as PASS with an observation, FAIL with reproduction, or NOT TESTED; never infer pass from no complaint.

## Underlying application

Open a normal interactive app beneath the active insect overlay. A text editor and a draggable/scrollable native test window are sufficient when accompanied by event logs or clear observations. Keep that application, not Settings, focused.

| Test | Required observation |
|---|---|
| Single click | Underlying control receives the click at a point visibly occupied by an ant and at a transparent point. |
| Double click | Underlying app receives its normal double-click action; no focus/activation changes to the overlay. |
| Drag | Begin beneath an ant, cross the entire overlay and release; underlying selection/drag continues uninterrupted. |
| Wheel/trackpad scroll | Both directions and smooth scrolling reach the underlying app. |
| Mouse movement | Hover/motion continue normally; no invisible borders resize or capture the pointer. |
| Keyboard | Typed text/shortcuts continue in the already-focused app; no overlay keyboard focus. |
| Panic | Press Ctrl+Alt+Shift+H while the other app has focus. Overlays disappear on key press, without waiting for release. Holding must not reshow them. |
| Task switching | Overlay absent as an ordinary Alt-Tab/Cmd-Tab/Dock/taskbar window; utility menu/tray remains reachable. |
| Settings separation | Settings is interactive when explicitly opened; closing it does not stop ants or leave an input-trapping transparent window. |
| Recreate/failure | A renderer failure hides affected overlays before reconstruction; utility Quit remains available. Repeat the input checks after recovery. |

Do not inject game processes, install drivers or defeat anti-cheat/protected surfaces to run a test. Test normal apps first. True exclusive/protected fullscreen may suppress the overlay and is an honest coverage limit, not a reason to raise window level repeatedly.

## Display and lifecycle matrix

Repeat basic input and panic checks on each enabled monitor, including a negative virtual-desktop origin and mixed DPI/backing scale. Test continuous and independent modes, changing resolution/scale/rotation, unplug/replug, full-screen video/Spaces, suspend/wake and session lock/unlock. Check the physical 2/3/4 mm fixtures with a ruler/reference and inspect normal-viewing-distance motion at each available refresh rate. Mark unavailable hardware cases NOT TESTED.

Run an actual install/uninstall cycle: no startup by default, opt-in owned startup works, opting out works, uninstall removes only its own startup value/file and installed payload, unrelated entries and user files survive. Do not run destructive registry-folder cleanup as a workaround.

A failure in click-through, panic visibility or no-focus safety blocks publication for that target. Save the report with the package artifacts. A successful compile or install does not override it.

## Supplied receiver

Run `cargo run --locked --release -p desktop-app --example input_probe -- --output input-with-overlay.json` from `app/`. Keep its normal window focused beneath the visibly active packaged overlay. Escape/Close saves event counts atomically. Repeat hidden with a new output path. No typed content is logged, and the report deliberately leaves native qualification false. Record double-click behavior manually in an ordinary application as well; counts alone are not semantic proof. See `docs/TESTING.md` for the tool boundary.
