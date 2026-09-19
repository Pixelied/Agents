# Platform support and acceptance boundary

| Capability | Current evidence boundary |
|---|---|
| Deterministic physics/config/calibration math | Automated tests; see current command results |
| Procedural rendering | Actual offscreen backend tests, not compositor acceptance |
| macOS adapter | Native compilation/unit/package checks; interactive launch/input still unqualified |
| Windows adapter | Native compilation/unit/package checks; interactive launch/input still unqualified |
| Physical 2/3/4 mm size | Mathematical conversion tests; ruler/card inspection on each real display remains required |
| Continuous monitor migration | Model/controller tests; real mixed-DPI hot-plug remains required |
| Headless stress timings | Valid only for recorded backend/host; not a native FPS certification |
| DMG/MSI packaging | Native recipes and structural tests; only actual produced native artifacts count as packages |
| Linux desktop overlay | Deferred; Linux here is a developer harness |

Ordinary compositor overlays cannot promise coverage of secure desktops,
protected video, login UI or true exclusive fullscreen. The app does not inject
into processes, hook graphics, bypass anti-cheat, install drivers or inspect
screen contents. Native process/window metadata can be unavailable under OS
privacy restrictions; conservative hiding and diagnostics are preferable to
pretending that exclusions are verified.

Release acceptance requires native clicks, double-clicks, drag, wheel, mouse
motion and keyboard delivery to an underlying application, plus panic hide
while another application is focused. Cross-compilation and policy tests do
not prove those behaviors. Actual macOS Spaces/Mission Control, Windows
Alt-Tab/taskbar, display rotation, hot-plug and sleep/wake must be recorded.

Signing/notarization are independent of functional implementation. Development
artifacts may be ad-hoc/unsigned, but must be labelled accordingly. No native
artifact exists merely because a packaging script or workflow exists.
