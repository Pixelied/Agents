# Insect Realism Desktop App — Design Specification

Date: 2026-09-17
Status: Approved design draft awaiting final human review
Task: `design-insect-realism-desktop-app-20260917-a317`

## 1. Purpose

Build a polished desktop utility that makes tiny insects appear to be physically crawling on the user's monitor glass rather than living inside an application window. The first release targets macOS and Windows as first-class platforms, with Linux deferred until the core is stable.

The application is an invisible background utility: it launches quietly, lives in the macOS menu bar or Windows notification area, renders transparent click-through overlays above ordinary desktop content, and exposes a small settings window only when requested.

The visual and behavioral benchmark is a convincing 2–4 mm ant at true physical display scale. Ants are the primary species and must reach the highest fidelity first. Version 1 may also ship one or two secondary tiny creatures, but only if the research pack provides sufficiently strong evidence, legal reusable assets or measurements, and a locomotion model that can be implemented without weakening ant quality or performance.

The app consumes the separate Insect Realism Mega Pack as its evidence source. It does not repeat the research pipeline at runtime and does not invent arbitrary biology when evidence is missing.

## 2. Locked product decisions

The following decisions are fixed for the implementation plan unless the user explicitly changes them later:

- Normal app shape: invisible utility, not a conventional always-open desktop window.
- First-class v1 platforms: macOS and Windows. Linux follows after the core is stable.
- Rendering strategy: procedural/hybrid 2.5D, optimized for tiny real-world screen size rather than conventional game-style full 3D.
- Scale target: architecture for 1,000+ simultaneous creatures per monitor; ordinary presets remain far below that.
- Cursor interaction: optional and subtle; disabled in the realistic preset.
- Multi-monitor behavior: both continuous-surface and independent-monitor modes.
- Overlay coverage: attempt to appear everywhere the normal OS compositor permits, including normal apps, full-screen video, presentations, and games, with an optional compatibility/safe mode and per-app exclusions.
- No game/process injection, graphics API hooking, kernel components, anti-cheat bypasses, or security-boundary circumvention.
- Settings model: presets first, advanced biology/simulation controls hidden behind an Advanced section.
- Presets: Realistic, Light Infestation, Heavy Infestation, Nightmare, and Custom.
- Secondary creatures: research selects the strongest one or two candidates rather than hard-coding a species choice now.
- Final Astra handoff: one continuous request that executes the entire implementation plan end-to-end. Milestones are internal checkpoints, not reasons to stop and ask the user to continue.

## 3. Execution-base and repository rule

The repository is a coordinated multi-agent workspace. The desktop app must not overwrite or collide with active research work.

At spec-authoring time, the repository contains an active fresh Mega Pack rebuild task, `rebuild-insect-mega-pack-20260917-c82e`, whose project work is intentionally isolated from older insect lineages. The app implementation must therefore resolve its base at execution time rather than blindly starting from an older insect branch name.

The implementation agent must:

1. Refresh coordination state from `main` and inspect active tasks, leases, handoffs, and the current canonical or intended insect research lineage.
2. Identify the newest validated Mega Pack project state by content, ancestry, task intent, and verification evidence rather than by branch naming alone.
3. Never edit a scope covered by another unexpired lease.
4. If the final Mega Pack lineage is complete and canonical, branch the app feature from that project base.
5. If the app genuinely depends on a still-unmerged completed research branch, stack intentionally on that branch and document the dependency.
6. If the research task is still actively changing the same project scope, do not collide with it. The app plan may prepare its own isolated app branch only once a safe base exists.
7. Preserve all prior research branches and implementations. No destructive cleanup is part of this app task.

The intended application layout is under the selected final insect project:

```text
projects/<selected-insect-project>/
├── app/
│   ├── Cargo.toml
│   ├── crates/
│   ├── assets/
│   ├── tools/
│   ├── tests/
│   └── benchmarks/
├── research/
├── INSECT_REALISM_MEGA_PACK/
├── scripts/
└── docs/
```

The exact project id is resolved from repository truth at execution time; implementation must not hard-code an obsolete project lineage merely because it existed when this spec was written.

## 4. Runtime architecture

Use Rust as the shared implementation language, `wgpu` as the renderer abstraction, and thin native platform adapters for macOS and Windows where generic windowing libraries do not provide reliable overlay behavior.

The application is one lightweight background utility, internally split into strongly isolated subsystems.

### 4.1 App shell

Responsibilities:

- process lifecycle;
- startup and shutdown;
- menu-bar/system-tray integration;
- opening/closing the settings window;
- global emergency hide hotkey;
- pause/resume state;
- coordination between platform adapter, simulation, renderer, and settings.

The app shell must not own biology, rendering details, or platform-specific overlay implementation.

### 4.2 Biology/profile layer

The Mega Pack remains the scientific source of truth. Runtime does not parse papers or large research datasets.

A build-time profile compiler converts approved research outputs into a compact, versioned runtime bundle containing implementation-ready measurements and distributions. Every derived runtime parameter retains source/provenance identifiers so suspicious behavior can be traced back to research evidence.

Conceptual flow:

```text
Mega Pack derived evidence
        ↓
profile schema validation
        ↓
profile compiler
        ↓
versioned runtime creature profiles
        ↓
application
```

Malformed or unsupported profiles fail validation. The app must never silently replace missing evidence with arbitrary movement constants.

### 4.3 Simulation core

The simulation is platform-independent Rust and uses physical millimeters as its world unit.

Responsibilities:

- fixed-step creature simulation;
- individual biological variation;
- behavioral state and transition logic;
- locomotion intentions;
- spatial indexing;
- trail/pheromone field;
- encounter behavior;
- edge behavior;
- spawn/exit dynamics;
- deterministic seeded execution for tests;
- generation of compact render state.

The renderer may interpolate at 60–240+ Hz while biological decision-making runs at a lower fixed cadence. A target around 30 Hz for high-level biological updates is acceptable if motion integration and render interpolation remain smooth and validation shows no visual degradation.

For large populations, use compact cache-friendly data layouts, preallocated capacity, spatial partitioning, and staggered expensive decisions. Avoid heavyweight per-agent object graphs and avoid all-pairs interaction.

### 4.4 Creature abstraction

Use a generic creature-facing boundary only where it adds real value. Ants remain allowed to use specialized internal structures.

A creature profile can provide:

- morphology;
- physical size distributions;
- locomotion model;
- behavior parameters;
- render model/LOD data;
- spawn rules;
- evidence metadata.

Secondary creatures must not force the ant implementation into a lowest-common-denominator design.

### 4.5 Renderer

Use `wgpu` with GPU-instanced procedural/hybrid 2.5D creatures.

The CPU should send compact per-instance state such as:

- physical position;
- heading;
- physical scale;
- morphology variant;
- gait phase;
- speed;
- turn amount;
- behavioral pose;
- antenna state;
- LOD;
- stable variation seed;
- contact/material parameters.

Do not issue one draw call per creature. Do not run a heavyweight skeletal or rigid-body engine per ant.

### 4.6 Display model

Each monitor has:

- OS logical bounds;
- physical pixel dimensions;
- effective physical dimensions/calibration;
- refresh rate;
- scale factor;
- stable display fingerprint;
- topology relationships to other monitors.

Simulation coordinates are physical millimeters. Per-display transforms convert them into render pixels.

### 4.7 Platform adapters

Shared simulation/rendering code must not pretend macOS and Windows expose identical overlay semantics.

`platform_macos` owns AppKit/Core Graphics integration for:

- transparent borderless overlays;
- click-through/non-activating behavior;
- window level;
- Retina/backing scale;
- Spaces/full-screen behavior;
- menu bar;
- launch at login;
- global hotkeys;
- monitor discovery and topology events.

`platform_windows` owns Win32/DWM integration for:

- transparent overlay windows;
- explicit hit-test pass-through;
- non-taskbar/tool-style behavior;
- topmost/window ordering;
- per-monitor DPI awareness;
- system tray;
- startup registration;
- global hotkeys;
- monitor topology and full-screen handling.

Linux is implemented later behind the same platform boundary, with X11/Wayland support documented honestly by compositor capability.

## 5. Ant simulation design

### 5.1 Persistent individual variation

Each ant receives a stable identity derived from evidence-backed distributions rather than random values changing every frame.

Individual traits may include:

- preferred speed;
- stride characteristics;
- turning tendency;
- pause tendency;
- direction persistence;
- edge-following tendency;
- trail sensitivity;
- encounter response;
- body-size variation;
- antenna behavior variation;
- short-lived behavioral memory.

Two ants in the same internal state should not move identically.

### 5.2 Behavior states

The implementation may use internal states such as:

- exploratory walking;
- directed transit;
- probing/searching;
- pause;
- edge following;
- trail following;
- ant encounter/antennation;
- collision avoidance;
- disturbance escape;
- grooming/rest where evidence supports it.

State changes are probabilistic and context-sensitive, influenced by recent history and individual traits. Visual behavior must blend rather than snap between obviously scripted modes.

### 5.3 Trajectory realism

Do not use random waypoints, Perlin noise, or constant steering noise as the primary ant movement model.

Movement should be derived from the Mega Pack's measured trajectory statistics where available, including distributions such as:

- straight-run duration;
- speed changes;
- angular velocity;
- curvature;
- pause duration;
- direction persistence;
- wall/edge following;
- encounter effects.

The engine can sample statistically valid local motion intentions and integrate them smoothly.

### 5.4 Gait coupling

Animation and translation must agree.

Walking speed drives gait phase and stride frequency. Stopping stops walking. Rapid movement changes posture and gait. Probing increases exploratory antenna behavior. Antennation uses encounter-specific pose behavior.

Where applicable to the selected ant morphology, normal walking should reflect coordinated alternating tripod gait rather than an unrelated looping animation.

### 5.5 Antennae

Antennae are behavior-driven, asymmetric, and correlated over time. Do not animate them with simple independent sine waves.

Behavior influences can include:

- wide exploration while probing;
- forward bias in directed transit;
- directed antennation during encounters;
- edge/surface investigation;
- pauses and reversals.

Low LODs may reconstruct antenna motion procedurally on the GPU from compact state.

### 5.6 Encounters

Nearby creatures use spatial queries, not all-pairs checks.

Ant encounters may result in:

- no reaction;
- minor course adjustment;
- slowing;
- brief stop;
- antennation;
- following;
- avoidance.

Avoid obvious hard-circle particle bouncing. Minor graphical overlap is preferable to arcade-style repulsion.

### 5.7 Trails

Use a coarse, decaying surface field rather than a particle-heavy pheromone simulation.

The field may store intensity and direction information and should support emergent temporary routes, loose columns, reinforcement, and gradual abandonment.

Trail updates must be amortizable across frames for large populations.

### 5.8 Screen edges and monitor crossings

A true outer display edge behaves like the physical edge of the traversable glass. Ants may investigate, turn, follow the edge, or disappear beyond it; they should not simply bounce.

In continuous multi-monitor mode, touching configured display edges form topology connections. Crossing preserves physical position, direction, and speed in millimeters per second even when pixel density differs.

Independent-monitor mode treats every display boundary as exterior.

### 5.9 Spawn and exit behavior

Normal creatures enter from plausible off-screen/edge regions. Do not materialize ants in the middle of the display unless an explicit developer or user action requests immediate spawning.

Population changes should normally use entry and exit rates so infestation density evolves visibly instead of popping.

### 5.10 Presets affect population, not fake biology

Realistic, Light, Heavy, and Nightmare presets primarily alter population-level pressure, density, route persistence, and composition. They must not turn Nightmare into unrealistic 5× ant speed.

### 5.11 Cursor interaction

Cursor response is a separate optional disturbance field and is OFF in the realistic preset.

When enabled, fast nearby cursor motion can create a small local disturbance causing brief acceleration or redirection. There is no health system, feeding, chasing, direct collision gameplay, or UI semantics.

### 5.12 Physical-surface rule

The simulation knows about physical display geometry, other creatures, edges, trails, and optional disturbance fields. It does not understand buttons, text, browsers, icons, games, videos, or windows underneath it.

A useful review question for every behavior is: would this still make sense if the pixels underneath the ant were replaced with a different application?

## 6. Rendering design

### 6.1 Physical-size first

Creature anatomy is expressed in millimeters. Display calibration maps those measurements to pixels.

A 3 mm ant must remain approximately 3 mm on a Retina laptop display, a 4K monitor, or a Windows display using UI scaling. Logical UI scale must not alter biological size.

### 6.2 Procedural articulated ant

The ant renderer should prioritize the cues that survive at 2–4 mm:

- three separated body masses;
- narrow waist;
- six legs;
- paired antennae;
- correct body proportions;
- stable orientation while turning.

The conceptual rig contains head, thorax, abdomen, six segmented legs, and two segmented antennae. Most articulation should be reconstructed on GPU from compact instance state.

### 6.3 Thin appendage rendering

Legs and antennae can be subpixel-width. Basic one-pixel rasterized lines are unacceptable if they shimmer, disappear, or double in thickness during rotation.

Candidate techniques include analytically anti-aliased screen-space capsules/strips, signed-distance primitives, and coverage-based alpha. The chosen implementation must be validated across 360-degree rotation.

### 6.4 Lighting/material appearance

A real insect on an emissive panel primarily blocks/scatters the underlying light.

Default rendering therefore favors a dark silhouette with subtle material variation rather than game-style studio lighting.

On bright content, the body and legs become strongly visible. On dark content, visibility may naturally decrease. Do not add glowing outlines merely to keep ants visible.

Prohibit obvious drop shadows, large ambient-occlusion halos, bloom, cartoon rim lights, and oversized white specular dots. Any contact darkening should be tiny and physically bounded.

### 6.5 Stable individual morphology

Population variation may include body length, width, abdomen size, leg proportions, and coloration, drawn from approved ranges and generated from stable per-creature seeds.

An individual must not change shape from frame to frame.

### 6.6 LOD

Use physical pixel coverage rather than arbitrary camera distance.

Conceptual levels:

- Tiny LOD: very cheap, carefully filtered silhouette with simplified procedural legs/antennae.
- Standard LOD: primary articulated appearance with segmentation, gait, antennae, and subtle material response.
- Detailed LOD: additional fidelity for high-PPI, magnification, deliberately enlarged creatures, screenshots, or debug views.

Transitions use hysteresis and must not visibly pop.

### 6.7 Refresh-rate independence

Biology does not need to update at 240 Hz, but rendering should interpolate smoothly at the active monitor refresh rate, including 60, 120, 144, 165, and 240 Hz.

Do not add heavy artificial motion blur. Any temporal coverage effect must be subtle and evidence/validation driven.

### 6.8 No screen capture dependency

The renderer must not require continuous screen capture, OCR, or content inspection. Transparent compositing is enough for a dark creature to visually interact with whatever is behind it.

This reduces permissions, privacy risk, anti-cheat friction, and overhead.

### 6.9 Validation scenes

Developer mode must render deterministic scenes covering:

- one stationary 2 mm creature;
- one 3 mm walking ant;
- one 4 mm turning ant;
- 100, 500, and 1,000+ creatures;
- all headings/gait phases;
- bright and dark backgrounds;
- high-contrast patterns;
- representative monitor PPIs;
- Retina/high-DPI;
- LOD transitions.

The main realism review is at calibrated 1:1 physical scale and normal viewing distance, not only enlarged debug views.

## 7. Overlay and platform behavior

### 7.1 One overlay per monitor

Each physical display owns a transparent, borderless overlay mapped exactly to its bounds. Different refresh rates, DPI values, scale factors, orientations, and hot-plug events remain independently manageable.

### 7.2 Input pass-through is mandatory

The overlay must never steal normal mouse or keyboard input.

Required properties:

- non-activating;
- no normal focus acquisition;
- click/drag/scroll pass-through;
- no invisible resize borders;
- no normal taskbar/Dock presence;
- no pointer capture;
- no accidental Alt-Tab/Cmd-Tab entry as an ordinary window.

The settings window is separate and interactive.

### 7.3 Fail-safe visibility

An overlay may only become visible after the platform adapter has successfully established the required transparent/non-activating/click-through state.

If those guarantees cannot be verified, keep the overlay hidden and surface a diagnostic through the normal utility UI. A transparent always-on-top input-trapping failure is unacceptable.

### 7.4 Coverage policy

Default mode attempts to show insects everywhere the ordinary OS compositor permits: desktop, normal applications, video, presentations, borderless games, and composited full-screen applications.

Do not inject into games, hook graphics APIs, alter other processes, bypass protected surfaces, or defeat secure desktops. True exclusive full-screen, protected video, login/security surfaces, or app-specific suppression may prevent overlays; document these limitations honestly.

### 7.5 Compatibility/safe mode

Provide an optional mode and per-app exclusions that can hide overlays over selected applications or full-screen cases.

Rules are local and based on process/window metadata, not screen-content inspection.

### 7.6 Emergency hide

A configurable global panic hotkey instantly hides all overlay windows with no animation. The tray/menu provides the same action.

Hide All freezes the simulation and is temporary. Pause is an explicit persistent disabled state. Quit exits the process.

### 7.7 System UI

Creatures conceptually occupy the physical glass, so they do not treat the menu bar, taskbar, Dock, browser chrome, or other UI pixels as physical obstacles. OS window ordering may still place protected/system surfaces above the overlay.

### 7.8 Startup and lifecycle

Launch-at-login is optional. Startup should be silent: no splash screen, console, or settings window.

Sleep suspends simulation time. Wake resumes without simulating the hours spent asleep.

Display attach/remove, resolution change, scaling change, rotation, and sleep/wake must be handled without application restart.

### 7.9 Hidden-state efficiency

When all overlays are hidden, render submission stops. Panic-hidden state may pause simulation entirely. When only one monitor is hidden, its render work is skipped while enough simulation state is maintained for correct restoration.

### 7.10 Renderer/device loss

On renderer/device failure:

1. hide affected overlay safely;
2. keep menu/tray responsive where possible;
3. attempt bounded renderer recreation;
4. rebuild GPU resources from CPU state;
5. show the overlay again only after click-through safety is revalidated.

Never leave an opaque or broken topmost rectangle covering the user's desktop.

## 8. Settings and UX

### 8.1 First run

First launch verifies overlay safety, display geometry/calibration confidence, and global hotkey availability.

If physical display dimensions are trustworthy, start with the Realistic preset. If they are not, run a short physical-size calibration instead of guessing.

A simple calibration reference such as a standard credit-card rectangle is preferred. Calibration is stored per display fingerprint.

### 8.2 Main settings surface

Keep the normal settings UI small. Primary controls include:

- Enable creatures;
- Preset;
- Creature population;
- Per-monitor enable/disable;
- Launch at login;
- Cursor reaction;
- Continuous vs independent monitor mode;
- Compatibility/Safe Overlay Mode;
- Panic hotkey;
- Secondary creatures;
- Advanced.

### 8.3 Presets

Preset definitions are data, not hard-coded branches of simulation logic.

Realistic: research-backed sizes, activity, and behavior; subtle population; cursor response off.

Light Infestation: increased visibility/population while staying plausible.

Heavy Infestation: larger colony and more persistent group/trail activity.

Nightmare: high population pressure, potentially 1,000+ creatures, while individual locomotion stays biologically plausible.

Custom: automatically selected when the user changes values outside a preset.

### 8.4 Advanced settings

Advanced controls may expose population cap, spawn/exit rates, activity multiplier, trail influence, clustering strength, pause tendency, edge following, secondary creature mix, cursor disturbance, simulation seed, creature scale, and developer rendering options.

The UI should mark the research-backed realistic range where relevant. A deliberate creature scale multiplier does not alter monitor calibration.

### 8.5 Secondary creatures

Populate secondary creature controls dynamically from packaged profiles. The UI must not assume fixed species names at compile time.

### 8.6 Configuration storage

Use a small versioned config with:

- atomic writes;
- schema version;
- migrations;
- corruption recovery;
- per-display calibration;
- per-app exclusions;
- no secrets;
- no telemetry identifier.

On corrupt config, preserve the bad file for diagnostics, restore sane defaults, and keep the app usable.

### 8.7 Developer mode

A hidden/advanced developer mode exposes diagnostics such as:

- creature IDs;
- behavior-state coloring;
- spatial grid;
- trail field;
- LOD visualization;
- physical monitor coordinates;
- deterministic seed;
- forced population;
- rendering validation scenes;
- performance counters.

Do not expose these in normal user flow.

### 8.8 Telemetry and updates

No account system, cloud sync, analytics SDK, or mandatory updater is required in v1. Runtime must work offline.

A future updater may be added later without changing the simulation/rendering architecture.

## 9. Performance and reliability

### 9.1 Core performance target

Required baseline: 1,000 creatures on one monitor at 60 FPS on ordinary modern hardware, using release-mode binaries and full normal rendering/interaction systems.

The architecture should allow more than 1,000 per monitor even if total throughput depends on GPU/CPU capability.

Additional requirements:

- no O(n²) neighbor loop;
- no per-creature draw call;
- no normal per-frame heap allocation in simulation hot paths;
- no spawn/despawn hitching;
- simulation speed independent from render FPS;
- hidden overlays stop render submission;
- paused state approaches idle resource usage.

### 9.2 Benchmark scenes

Maintain deterministic benchmark scenes:

- Realistic: ~50 ants;
- Heavy: ~500 creatures;
- Required stress: 1,000 creatures with interactions/trails/rendering;
- Extreme: 2,000–5,000 creatures for stability and graceful degradation.

Extreme mode does not need the same hard FPS guarantee but must not crash, leak unbounded memory, or corrupt simulation state.

### 9.3 Metrics

Record simulation time, behavior time, spatial time, trail time, render preparation, GPU frame time, upload size, overlay overhead, total frame time, allocation count, memory, and LOD counts.

Report median, p95, p99, and worst observed spikes rather than averages only.

## 10. Test strategy

### 10.1 Unit tests

Cover:

- millimeter/pixel conversion;
- calibration math;
- display transforms;
- topology adjacency;
- deterministic RNG;
- config migration;
- preset validation;
- profile parsing;
- locomotion integration;
- state transitions;
- trail decay;
- spatial indexing;
- LOD selection.

### 10.2 Property/invariant tests

Examples:

- no NaN/infinite creature state;
- valid orientation and size;
- valid surface transitions;
- bounded trail values;
- deterministic seeded behavior;
- random monitor layouts never create invalid transforms;
- spatial hash matches a brute-force reference on small populations.

### 10.3 Deterministic simulation snapshots

Fixed seed/profile/duration scenarios produce stable compact snapshots or tolerance-based signatures. Changes require intentional review.

### 10.4 Renderer tests

Use deterministic scenes and image/reference checks for headings, gait phases, LODs, PPI ranges, backgrounds, body-size bounds, and subpixel appendages.

### 10.5 Platform smoke tests

macOS and Windows must verify:

- transparency;
- click-through;
- no focus acquisition;
- global panic hotkey;
- tray/menu survival across overlay recreation;
- monitor hot-plug behavior;
- DPI/scale changes;
- sleep/wake;
- renderer failure safety.

### 10.6 Input safety release blocker

With the overlay active, a test app underneath must receive clicks, double-clicks, dragging, scrolling, mouse motion, and keyboard input normally. Failure blocks release.

### 10.7 Soak testing

Run long sessions with heavy populations, spawn/despawn cycles, preset changes, hide/show cycles, config writes, topology changes where possible, and renderer recreation. Look for memory growth, OS handle leaks, GPU leaks, corrupt state, and increasing frame times.

## 11. Packaging and distribution

### 11.1 macOS

Produce a normal `.app` bundle with no terminal requirement and package a direct-distribution `.dmg`.

Primary target is Apple Silicon. Intel/Universal 2 support should be included when dependency/toolchain cost remains reasonable.

Use only necessary entitlements.

Signing/notarization is optional during development and may require user-supplied credentials. Missing signing credentials are an external release blocker, not a reason to stop implementation.

### 11.2 Windows

Primary target is x64 Windows. Produce a normal GUI binary with no console window plus a conventional installer such as MSI or similarly straightforward package.

Support clean uninstall and user-configurable launch at login. Do not require kernel drivers, services, or admin privileges unless a documented OS requirement truly makes them necessary.

Missing code-signing credentials do not block functional implementation or unsigned test artifacts.

## 12. CI and versioning

Use project-specific CI on the selected project lineage.

Normal CI should run format, clippy/lints, unit tests, property tests, profile validation, deterministic simulation tests, and macOS/Windows builds.

Separate workflows/jobs may handle renderer validation, benchmarks, and releases because hosted CI GPU capabilities can be limited.

Release tags trigger clean release builds, full verification, profile compilation, packaging, signing/notarization when credentials exist, checksums, and release artifacts.

Track separate versions for:

- application;
- biology/profile bundle;
- runtime profile schema.

This allows rendering changes to be distinguished from biology-data changes.

## 13. Documentation deliverables

The implementation must leave behind maintainable documentation, including at least:

- `README.md` — build/run/test basics;
- `ARCHITECTURE.md` — subsystem boundaries and dependency direction;
- `PLATFORM_SUPPORT.md` — verified behavior and known OS/full-screen limitations;
- `BIOLOGY_PROFILE_FORMAT.md` — research-to-runtime profile contract;
- `PERFORMANCE.md` — benchmark method and latest verified results;
- `RELEASE.md` — packaging, signing, notarization, and release steps;
- `TESTING.md` — automated and manual acceptance matrix.

## 14. One-shot Astra execution requirement

The implementation plan produced from this spec must be designed to consume a single Astra request.

The request tells Astra to continue automatically through the entire assignment. Internal milestones and commits are checkpoints only.

Expected execution flow:

```text
refresh coordination + inspect repository
        ↓
resolve newest validated Mega Pack lineage
        ↓
claim safe app scope and create/use correct feature branch
        ↓
create Rust workspace and test harness
        ↓
implement profile compiler
        ↓
implement physical display model
        ↓
implement simulation/spatial/trails
        ↓
implement ant locomotion and behavior
        ↓
implement procedural renderer
        ↓
implement macOS overlay
        ↓
implement Windows overlay
        ↓
implement tray/menu/settings/calibration
        ↓
implement multi-monitor + compatibility behavior
        ↓
qualify/integrate secondary creatures if evidence gate passes
        ↓
profile and optimize 1,000+ creature workloads
        ↓
package macOS + Windows artifacts
        ↓
run full tests + benchmarks + validation
        ↓
fix failures and rerun
        ↓
document verified results and limitations
        ↓
final verification and handoff
```

Astra should autonomously investigate and fix ordinary bugs, test failures, architecture mismatches, and performance misses discovered during execution rather than returning after each problem.

Astra may stop early only for genuine external blockers that require unavailable human input or hardware, such as missing signing credentials, repository permission denial, unsupported protected OS surfaces, or unavailable hardware-only test environments. It must complete everything else first and document the blocker precisely.

The plan must require a lightweight execution checklist/log in the repository so a long-running agent session does not lose track of completed milestones.

## 15. Definition of done

The app is not complete merely because source files exist.

Completion requires, as far as the available environment permits:

- macOS menu-bar utility launches without a terminal;
- Windows tray utility launches without a console;
- transparent overlays render correctly;
- normal input passes through;
- global panic hide works;
- true physical ant size is calibrated correctly;
- research-backed simulation runs deterministically in tests;
- gait/antennae visually follow locomotion and behavior;
- independent and continuous multi-monitor modes work;
- presets and settings persist safely;
- per-app compatibility exclusions work;
- 1,000-creature required stress benchmark meets the defined reference target on the available reference hardware;
- high-refresh interpolation works;
- no visible major LOD popping or subpixel-leg instability in validation scenes;
- failure paths never trap the desktop;
- long-running stress testing shows no unbounded leak or simulation degradation;
- installable macOS and Windows artifacts are produced;
- secondary creatures are included only if their evidence/quality gate passes;
- documentation records verified support and known limitations honestly;
- final release-mode test/benchmark outputs are inspected before any completion claim.

## 16. Non-goals for v1

Do not expand v1 with unrelated complexity unless required by the approved design:

- no accounts or cloud backend;
- no telemetry/analytics platform;
- no mandatory auto-updater;
- no App Store/Microsoft Store dependency;
- no game injection or cheat-style overlay integration;
- no screen understanding, OCR, or semantic interaction with UI content;
- no elaborate feeding/health/gameplay system;
- no full 3D physics engine per insect;
- no requirement to ship Linux in the first release;
- no requirement to ship a secondary creature whose research is weak.

The product succeeds by making tiny creatures look and move convincingly while remaining lightweight, safe, private, and nearly invisible as an application.
