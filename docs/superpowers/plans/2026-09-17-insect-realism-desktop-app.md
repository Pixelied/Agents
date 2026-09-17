# Insect Realism Desktop App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete macOS + Windows insect-realism desktop utility: research-backed ant simulation, true physical-size multi-monitor rendering, safe click-through overlays, presets/settings, 1,000+ creature performance, packaging, CI, validation, and release artifacts.

**Architecture:** A Rust workspace contains a platform-independent biology/simulation core, physical display model, `wgpu` procedural 2.5D renderer, small `egui` settings surface, and thin native macOS/Windows adapters. Build-time tooling compiles the approved Mega Pack evidence into versioned runtime creature profiles; runtime works offline and never needs screen capture or semantic knowledge of the UI underneath it.

**Tech Stack:** Rust stable, Cargo workspace, `wgpu`, WGSL, `winit`, `egui`/`egui-wgpu`, `serde`, `serde_json`, `toml`, `postcard`, `glam`, `rand_chacha`, `bytemuck`, `tracing`, `clap`, `sha2`, `tempfile`, `proptest`, `criterion`, `image`, native `windows` Win32 bindings, `objc2`/AppKit bindings, GitHub Actions, platform-native packaging tools.

**Spec:** `docs/superpowers/specs/2026-09-17-insect-realism-desktop-app-design.md`

## Global Constraints

- The app is an invisible utility: macOS menu-bar utility and Windows notification-area utility; no conventional always-open main window.
- macOS and Windows are first-class v1 targets. Linux is deferred until the core is stable.
- Rendering is procedural/hybrid 2.5D with true physical dimensions in millimeters.
- Architecture must support 1,000+ simultaneous creatures per monitor; ordinary presets remain lower.
- Cursor interaction is optional, subtle, and OFF in the Realistic preset.
- Support both continuous multi-monitor topology and independent-monitor mode.
- Default overlay coverage is maximum normal-compositor coverage; Compatibility/Safe Mode and per-app exclusions are optional controls.
- Never inject into games, hook graphics APIs, alter other processes, add kernel components, bypass anti-cheat, or circumvent protected OS/security surfaces.
- Presets are data: Realistic, Light Infestation, Heavy Infestation, Nightmare, Custom.
- Ants are the fidelity benchmark. Secondary creatures ship only if the Mega Pack evidence/quality gate passes.
- Runtime does not parse papers or large research datasets. It consumes validated, versioned compiled profiles.
- No account system, cloud backend, telemetry SDK, mandatory updater, continuous screen capture, OCR, or UI-semantic interaction in v1.
- The final execution is one continuous assignment. Milestones and commits are checkpoints only; do not stop after a task and ask the user to continue.
- Ordinary bugs, failing tests, integration issues, and performance misses discovered during execution must be investigated and fixed autonomously before final handoff.
- Stop early only for genuine external blockers requiring unavailable human credentials/hardware/permissions; complete all unaffected work first and document the blocker precisely.

## One-shot Astra invocation contract

The user intends to spend one Astra request on this build. When this plan is handed to Astra, the request should be exactly equivalent to:

> Read `docs/superpowers/specs/2026-09-17-insect-realism-desktop-app-design.md` and `docs/superpowers/plans/2026-09-17-insect-realism-desktop-app.md` in full. Execute the ENTIRE implementation plan as one continuous assignment. Start by refreshing repository coordination state and resolving the newest verified Mega Pack lineage exactly as Task 1 requires. Create/use the correct isolated implementation branch and obey all active leases. Then implement every task in order using test-driven development, run each required verification command, profile and optimize the 1,000+ creature target, build macOS and Windows artifacts as far as the available environment permits, fix ordinary failures without returning to me, and continue until the Definition of Done is satisfied or a genuine external blocker remains. Internal task boundaries and commits are checkpoints, NOT prompts to ask me whether to continue. Never claim completion from source existence alone; inspect release-mode test, benchmark, packaging, and platform-validation outputs before the final report.

## Execution bookkeeping

Task 1 creates `$APP_ROOT/APP_EXECUTION.md`. Every later task must update that file before its milestone commit with:

```markdown
## Task N - <name>
Status: complete | blocked
Commit: <sha>
Verification:
- `<command>` -> PASS/FAIL
Artifacts:
- <paths>
Notes:
- <measured facts, blockers, platform limitations>
```

`$APP_ROOT` is an execution-bound variable, not an unresolved placeholder. Task 1 resolves it from repository truth and records the exact relative path at the top of `APP_EXECUTION.md`; every later `$APP_ROOT/...` path means that resolved project root. Angle-bracket tokens such as `<unique>` in Task 1 are likewise instructions to substitute concrete values during that task and record them immediately; they must not survive into generated source/configuration files.

---

# File and crate map

The executor must create the following application subtree under the resolved project root unless the selected project already contains an equivalent, compatible file that should be extended rather than duplicated:

```text
$APP_ROOT/app/
├── Cargo.toml                       # workspace and shared dependency versions
├── rust-toolchain.toml              # pinned stable toolchain channel/components
├── crates/
│   ├── creature-profile/            # runtime profile schema + provenance
│   │   └── src/{lib.rs,schema.rs,validate.rs}
│   ├── display-model/               # mm/pixel calibration + monitor topology
│   │   └── src/{lib.rs,units.rs,display.rs,calibration.rs,topology.rs}
│   ├── simulation/                  # deterministic SoA simulation + ant behavior
│   │   ├── src/{lib.rs,clock.rs,state.rs,spatial.rs,trails.rs,behavior.rs,locomotion.rs,spawn.rs,snapshot.rs}
│   │   └── benches/spatial.rs
│   ├── rendering/                   # wgpu device, instances, WGSL, validation scenes
│   │   ├── src/{lib.rs,instance.rs,renderer.rs,lod.rs,validation.rs}
│   │   └── shaders/{ant.wgsl,composite.wgsl}
│   ├── platform-api/                # cross-platform overlay/lifecycle contracts
│   │   └── src/{lib.rs,overlay.rs,event.rs,app_identity.rs}
│   ├── platform-macos/              # AppKit/CoreGraphics native behavior
│   │   └── src/{lib.rs,overlay.rs,status_item.rs,hotkey.rs,login.rs,display.rs}
│   ├── platform-windows/            # Win32/DWM native behavior
│   │   └── src/{lib.rs,overlay.rs,tray.rs,hotkey.rs,startup.rs,display.rs}
│   ├── settings/                    # versioned config, presets, egui settings UI
│   │   └── src/{lib.rs,config.rs,migrate.rs,preset.rs,ui.rs,calibration_ui.rs}
│   └── desktop-app/                 # process lifecycle + subsystem orchestration
│       ├── src/{main.rs,app.rs,lifecycle.rs,compatibility.rs,diagnostics.rs}
│       └── benches/{simulation.rs,stress.rs}
├── tools/
│   └── profile-compiler/
│       └── src/main.rs
├── assets/
│   ├── creature-profiles/
│   └── presets/
├── tests/
│   ├── input-safety/README.md
│   └── soak/README.md
└── packaging/
    ├── macos/{Info.plist,build-dmg.sh}
    └── windows/{wix/Product.wxs,build-msi.ps1}
```

Project docs live at:

```text
$APP_ROOT/README.md
$APP_ROOT/docs/ARCHITECTURE.md
$APP_ROOT/docs/PLATFORM_SUPPORT.md
$APP_ROOT/docs/BIOLOGY_PROFILE_FORMAT.md
$APP_ROOT/docs/PERFORMANCE.md
$APP_ROOT/docs/RELEASE.md
$APP_ROOT/docs/TESTING.md
```

---

### Task 1: Resolve repository truth, acquire a safe scope, and start the execution log

**Files:**
- Read: `AGENTS.md`
- Read: `.agent-workspace.json`
- Read: `docs/protocols/coordination.md`
- Read: `docs/protocols/project-branches.md`
- Read: current insect tasks/leases/events/handoffs on `main`
- Create: `$APP_ROOT/APP_EXECUTION.md`

**Interfaces:**
- Consumes: repository coordination protocol plus the approved spec/plan.
- Produces: exact `APP_ROOT`, exact implementation branch, an exclusive non-conflicting app scope, and durable execution checklist used by every later task.

- [ ] **Step 1: Refresh coordination state and inspect all insect-related work before editing.**

Run from a synchronized checkout:

```bash
git fetch --all --prune
git switch main
git pull --ff-only
python agentctl.py agent-list
python agentctl.py task-list
```

Then inspect `AGENTS.md`, `.agent-workspace.json`, the project-branch protocol, current insect tasks, active leases, events, handoffs, and candidate project branches. Do not infer newest/canonical from branch names.

- [ ] **Step 2: Resolve the implementation base using explicit evidence.**

Create a short comparison note in your working notes containing, for every candidate Mega Pack lineage: branch/ref, project root, head SHA, task intent, active/released lease state, verification evidence, and whether its derived profiles/final pack are complete. Select the newest validated lineage by content + ancestry + intent + verification. If research is still actively changing the same project root under an unexpired lease, do not write into that root.

- [ ] **Step 3: Register/claim the smallest safe app scope and create the implementation branch.**

Use the repository CLI rather than manually inventing coordination records. Example shape, substituting the exact resolved project id and unique agent id:

```bash
python agentctl.py register --id astra-insect-app-<unique> --provider openai --model astra --capability rust --capability macos --capability windows --capability graphics
python agentctl.py task-create --id build-insect-realism-desktop-app-<unique> --title "Build insect realism desktop app" --created-by astra-insect-app-<unique> --objective "Implement the approved desktop app spec end-to-end" --scope "<resolved-project>/app" --scope "<resolved-project>/docs" --accept "Approved spec definition-of-done passes" --priority high
python agentctl.py claim --task build-insect-realism-desktop-app-<unique> --scope "<resolved-project>/app" --agent astra-insect-app-<unique> --ttl 240 --intent "Implement the one-shot desktop app plan"
```

If project docs are outside the app scope, claim that declared docs scope separately. Claims must reach `main` before implementation begins.

Create a feature branch from the resolved project base, for example:

```bash
git switch <resolved-project-base>
git pull --ff-only
git switch -c feat/insect-realism-desktop-app-<unique>
```

Never reuse a branch that already contains unrelated work.

- [ ] **Step 4: Create the execution log before application code.**

`APP_EXECUTION.md` must begin with:

```markdown
# Desktop App Execution Log

Resolved project root: `projects/<exact-id>`
Base branch: `<exact-ref>`
Base SHA: `<exact-sha>`
Implementation branch: `<exact-ref>`
Mega Pack/profile source: `<exact-ref-or-path>`
Agent/task: `<exact ids>`

## Global status
- [ ] Workspace foundation
- [ ] Profile compiler
- [ ] Display/calibration model
- [ ] Simulation + spatial + trails
- [ ] Ant behavior/locomotion
- [ ] Renderer + validation
- [ ] macOS platform adapter
- [ ] Windows platform adapter
- [ ] Settings/app shell
- [ ] Multi-monitor/compatibility/lifecycle
- [ ] Secondary creature gate
- [ ] Performance targets
- [ ] Packaging/CI
- [ ] Full release verification
```

- [ ] **Step 5: Commit only the execution-log/bootstrap decision.**

```bash
git add "$APP_ROOT/APP_EXECUTION.md"
git commit -m "chore: start insect desktop app execution"
```

Do not proceed if the scope is leased by another worker or the base cannot be established honestly.

---

### Task 2: Create the Rust workspace and lock cross-crate domain contracts

**Files:**
- Create: `$APP_ROOT/app/Cargo.toml`
- Create: `$APP_ROOT/app/rust-toolchain.toml`
- Create: `$APP_ROOT/app/crates/creature-profile/src/{lib.rs,schema.rs,validate.rs}`
- Create: `$APP_ROOT/app/crates/display-model/src/{lib.rs,units.rs}`
- Create: minimal `Cargo.toml` files for every crate in the file map
- Test: inline unit tests in `creature-profile` and `display-model`

**Interfaces:**
- Produces: `Millimeters`, `Vec2Mm`, `CreatureId`, `DisplayId`, `CreatureKind`, `CreatureProfile`, `RuntimeProfileBundle`, and `ProfileError`. Later tasks must reuse these exact public names.

- [ ] **Step 1: Write failing unit tests for physical units and profile validation.**

In `display-model/src/units.rs`:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn millimeters_are_not_logical_pixels() {
        let mm = Millimeters::new(3.0).unwrap();
        assert_eq!(mm.get(), 3.0);
        assert!(Millimeters::new(-1.0).is_err());
    }

    #[test]
    fn vec2_mm_rejects_non_finite_components() {
        assert!(Vec2Mm::new(f32::NAN, 1.0).is_err());
    }
}
```

In `creature-profile/src/validate.rs` add a test constructing a profile with a negative body length and assert validation returns `ProfileError::InvalidRange`.

- [ ] **Step 2: Run the focused tests and verify they fail.**

```bash
cd "$APP_ROOT/app"
cargo test -p display-model units -- --nocapture
cargo test -p creature-profile validate -- --nocapture
```

Expected: compile/test failure because the types are not implemented.

- [ ] **Step 3: Implement the shared domain types.**

Use the following public contract:

```rust
// display-model/src/units.rs
#[derive(Clone, Copy, Debug, PartialEq, PartialOrd)]
pub struct Millimeters(f32);

impl Millimeters {
    pub fn new(value: f32) -> Result<Self, UnitError>;
    pub const fn get(self) -> f32;
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Vec2Mm { pub x: f32, pub y: f32 }
impl Vec2Mm { pub fn new(x: f32, y: f32) -> Result<Self, UnitError>; }

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, serde::Serialize, serde::Deserialize)]
pub struct DisplayId(pub u64);
```

```rust
// creature-profile/src/schema.rs
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, serde::Serialize, serde::Deserialize)]
pub struct CreatureId(pub u64);

#[derive(Clone, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct CreatureKind(pub String);

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct EvidenceRef {
    pub source_id: String,
    pub field: String,
}

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct RangeF32 { pub min: f32, pub max: f32 }

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct CreatureProfile {
    pub id: CreatureKind,
    pub body_length_mm: RangeF32,
    pub body_width_mm: RangeF32,
    pub preferred_speed_mm_s: RangeF32,
    pub pause_duration_s: RangeF32,
    pub angular_velocity_rad_s: RangeF32,
    pub direction_persistence_s: RangeF32,
    pub evidence: Vec<EvidenceRef>,
}

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct RuntimeProfileBundle {
    pub schema_version: u32,
    pub profile_version: String,
    pub creatures: Vec<CreatureProfile>,
}

impl RuntimeProfileBundle {
    pub fn validate(&self) -> Result<(), ProfileError>;
}
```

Validation rejects non-finite values, reversed/negative physical ranges, duplicate creature ids, missing evidence for required biological fields, unsupported schema versions, and empty ant profile sets.

- [ ] **Step 4: Add the workspace dependency/version policy.**

Set workspace package version to `0.1.0` and runtime profile schema constant to `1`. Pin one compatible version of each shared dependency at the workspace level and inherit it from member crates. The first execution pass may resolve current compatible crate versions, but after that commit `Cargo.lock` and change dependencies only intentionally. Required dependency families: `serde`, `serde_json`, `toml`, `thiserror`, `glam`, `rand_chacha`, `rand_core`, `bytemuck`, `wgpu`, `winit`, `egui`, `egui-wgpu`, `tracing`, `tracing-subscriber`, `postcard`, `directories`, `proptest`, `criterion`, `image`, `pollster`, `raw-window-handle`, `clap`, `sha2`, `tempfile`; platform crates add `windows` or `objc2` families under target-specific sections.

- [ ] **Step 5: Run workspace checks.**

```bash
cargo fmt --all -- --check
cargo check --workspace --all-targets
cargo test -p display-model -p creature-profile
```

Expected: PASS.

- [ ] **Step 6: Update execution log and commit.**

```bash
git add "$APP_ROOT/app" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "build: establish insect app Rust workspace"
```

---

### Task 3: Compile the Mega Pack into strict runtime creature profiles

**Files:**
- Create: `$APP_ROOT/app/tools/profile-compiler/src/main.rs`
- Modify: `$APP_ROOT/app/crates/creature-profile/src/schema.rs`
- Modify: `$APP_ROOT/app/crates/creature-profile/src/validate.rs`
- Create: `$APP_ROOT/app/assets/creature-profiles/README.md`
- Create/generated: `$APP_ROOT/app/assets/creature-profiles/runtime-profiles.bin`
- Create/generated: `$APP_ROOT/app/assets/creature-profiles/runtime-profiles.report.json`
- Test: `$APP_ROOT/app/tools/profile-compiler/tests/compiler.rs`

**Interfaces:**
- Consumes: approved implementation-ready outputs from the resolved Mega Pack lineage.
- Produces: deterministic `RuntimeProfileBundle` serialized with `postcard` and two exact CLI forms:

```text
profile-compiler compile --input <mega-pack-root> --output <file> --report <json>
profile-compiler verify --bundle <file>
```

- [ ] **Step 1: Inspect the actual final Mega Pack derived outputs and map fields explicitly.**

Do not guess filenames from this plan. Read the final app-consumable specs/CSVs/JSONs and write a mapping table to `assets/creature-profiles/README.md` showing source file, source field, unit, runtime field, evidence id, and transformation. If the required ant measurements/trajectory distributions are absent, record a blocker; do not synthesize fake biology.

- [ ] **Step 2: Write compiler tests before the compiler.**

Create a tiny fixture directory under the compiler test temp dir with one valid ant profile and one invalid profile. Test:

```rust
#[test]
fn compiler_is_deterministic_and_rejects_bad_units() {
    let a = compile_fixture("valid").unwrap();
    let b = compile_fixture("valid").unwrap();
    assert_eq!(a.bytes, b.bytes);
    assert!(compile_fixture("negative_body_length").is_err());
}
```

Also assert the report contains the exact source/evidence ids consumed.

- [ ] **Step 3: Run the compiler tests and confirm failure.**

```bash
cargo test -p profile-compiler --test compiler -- --nocapture
```

- [ ] **Step 4: Implement the compiler as parse -> normalize units -> validate -> serialize.**

The `compile` subcommand emits a machine-readable JSON report containing:

```json
{
  "schema_version": 1,
  "profile_version": "research-release-id",
  "creatures": ["ant"],
  "source_files": [],
  "evidence_ids": [],
  "sha256": "hex-digest"
}
```

Use the profile schema's validators after conversion. Never clamp an invalid biological measurement silently. `verify` decodes the bundle, validates it, recomputes its digest, and exits nonzero on any schema/profile error.

- [ ] **Step 5: Compile and verify the real resolved Mega Pack.**

```bash
cargo run -p profile-compiler --release -- compile --input "<resolved-mega-pack-root>" --output assets/creature-profiles/runtime-profiles.bin --report assets/creature-profiles/runtime-profiles.report.json
cargo run -p profile-compiler --release -- verify --bundle assets/creature-profiles/runtime-profiles.bin
cargo test -p creature-profile -p profile-compiler
```

Expected: deterministic output and valid ant profile. If secondary profiles exist, leave them packaged but not enabled until Task 17's quality gate.

- [ ] **Step 6: Commit the compiler and only reproducible generated profile artifacts.**

```bash
git add "$APP_ROOT/app/tools/profile-compiler" "$APP_ROOT/app/crates/creature-profile" "$APP_ROOT/app/assets/creature-profiles" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: compile research evidence into runtime profiles"
```

---

### Task 4: Implement versioned settings and data-driven presets

**Files:**
- Create: `$APP_ROOT/app/crates/settings/src/{lib.rs,config.rs,migrate.rs,preset.rs}`
- Create: `$APP_ROOT/app/assets/presets/{realistic.toml,light.toml,heavy.toml,nightmare.toml}`
- Test: inline tests in settings crate

**Interfaces:**
- Produces: `AppConfig`, `PresetId`, `Preset`, `ConfigStore`, `ConfigError`.

Required public contract:

```rust
pub enum PresetId { Realistic, Light, Heavy, Nightmare, Custom }

pub struct AppConfig {
    pub schema_version: u32,
    pub enabled: bool,
    pub paused: bool,
    pub preset: PresetId,
    pub target_population: u32,
    pub cursor_reaction: bool,
    pub continuous_monitors: bool,
    pub launch_at_login: bool,
    pub safe_overlay_mode: bool,
    pub panic_hotkey: String,
    pub creature_scale: f32,
    pub developer_mode: bool,
    pub display_calibration: std::collections::BTreeMap<String, f32>,
    pub app_exclusions: Vec<AppExclusion>,
    pub secondary_creatures: std::collections::BTreeMap<String, bool>,
}

pub trait ConfigStore {
    fn load(&self) -> Result<AppConfig, ConfigError>;
    fn save_atomic(&self, config: &AppConfig) -> Result<(), ConfigError>;
}
```

- [ ] **Step 1: Write failing tests for defaults, preset boundaries, atomic save, and corrupt recovery.**

Tests must prove Realistic has cursor reaction OFF, Nightmare changes population pressure without multiplying biological speed, editing a preset-controlled value yields `Custom`, and corrupted config is preserved as `.corrupt-<timestamp>` before defaults are restored.

- [ ] **Step 2: Run focused tests and confirm failure.**

```bash
cargo test -p settings -- --nocapture
```

- [ ] **Step 3: Implement preset files as data and strict loaders.**

Each preset file defines only population-level controls: target population, spawn pressure, trail persistence multiplier, activity weighting, secondary composition. Biological speed ranges continue to come from creature profiles.

- [ ] **Step 4: Implement atomic configuration persistence and migration.**

Write to a sibling temporary file, `sync_all`, rename atomically where supported, then sync the parent directory where practical. Migrations must be explicit functions such as `migrate_v1_to_v2`; unknown future versions return an error rather than being overwritten.

- [ ] **Step 5: Verify and commit.**

```bash
cargo test -p settings
cargo clippy -p settings --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/settings" "$APP_ROOT/app/assets/presets" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add versioned settings and infestation presets"
```

---

### Task 5: Build physical display calibration and multi-monitor topology

**Files:**
- Create: `$APP_ROOT/app/crates/display-model/src/{display.rs,calibration.rs,topology.rs}`
- Modify: `$APP_ROOT/app/crates/display-model/src/lib.rs`
- Test: inline unit + property tests

**Interfaces:**
- Produces:

```rust
pub struct PixelSize { pub width: u32, pub height: u32 }
pub struct PhysicalSizeMm { pub width: f32, pub height: f32 }
pub struct DisplayFingerprint(pub String);
pub struct DisplaySurface {
    pub id: DisplayId,
    pub fingerprint: DisplayFingerprint,
    pub pixels: PixelSize,
    pub physical_mm: PhysicalSizeMm,
    pub scale_factor: f64,
    pub refresh_hz: f32,
    pub desktop_origin_px: (i32, i32),
    pub rotation_deg: u16,
}
pub struct DisplayCalibration { pub mm_per_physical_px: f32, pub confidence: CalibrationConfidence }
pub struct DisplayTopology { /* private graph */ }

impl DisplaySurface {
    pub fn mm_to_physical_px(&self, point: Vec2Mm, calibration: &DisplayCalibration) -> glam::Vec2;
}
impl DisplayTopology {
    pub fn rebuild(displays: &[DisplaySurface], calibrations: &std::collections::HashMap<DisplayId, DisplayCalibration>) -> Result<Self, TopologyError>;
    pub fn crossing(&self, display: DisplayId, point: Vec2Mm, velocity_mm_s: glam::Vec2) -> Option<SurfaceCrossing>;
}
```

- [ ] **Step 1: Write conversion and topology tests.**

Include a mixed-density case proving the same 3.0 mm body maps to different pixel lengths while preserving 3.0 mm physical size. Add a two-monitor continuous crossing test where velocity in mm/s is unchanged across the boundary.

- [ ] **Step 2: Add property tests for random sane monitor layouts.**

Using `proptest`, generate finite positive dimensions/scale factors and assert transforms are finite, invertible within tolerance, and topology never creates self-crossings or NaN overlap intervals.

- [ ] **Step 3: Run tests and confirm failure.**

```bash
cargo test -p display-model
```

- [ ] **Step 4: Implement calibration selection.**

Priority: trusted OS/EDID physical dimensions -> saved per-fingerprint manual calibration -> `CalibrationConfidence::NeedsManual`. Never fabricate a high-confidence PPI from logical UI scale alone.

Manual credit-card calibration uses ISO/IEC ID-1 width 85.60 mm as the reference. The UI slider ultimately stores effective `mm_per_physical_px`, not a fake monitor size.

- [ ] **Step 5: Implement topology using physical overlapping edge segments.**

Continuous mode only creates a connection where two display rectangles are adjacent in OS topology and have a physically overlapping edge after calibration. Independent mode disables all connections.

- [ ] **Step 6: Verify and commit.**

```bash
cargo test -p display-model
cargo clippy -p display-model --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/display-model" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: model calibrated physical display topology"
```

---

### Task 6: Create the deterministic fixed-step simulation and cache-friendly creature storage

**Files:**
- Create: `$APP_ROOT/app/crates/simulation/src/{clock.rs,state.rs,snapshot.rs}`
- Modify: `$APP_ROOT/app/crates/simulation/src/lib.rs`
- Test: inline tests

**Interfaces:**
- Produces:

```rust
pub struct SimulationConfig { pub tick_hz: u32, pub capacity: usize, pub seed: u64 }
pub struct EnvironmentSnapshot<'a> { pub topology: &'a DisplayTopology, pub cursor: Option<CursorDisturbance> }
pub struct Simulation { /* SoA buffers + clock + rng + fields */ }

impl Simulation {
    pub fn new(config: SimulationConfig, profiles: std::sync::Arc<RuntimeProfileBundle>) -> Result<Self, SimulationError>;
    pub fn advance(&mut self, real_dt: std::time::Duration, env: &EnvironmentSnapshot<'_>) -> Result<AdvanceReport, SimulationError>;
    pub fn snapshot(&self) -> SimulationSnapshot;
    pub fn set_target_population(&mut self, target: u32);
    pub fn set_paused(&mut self, paused: bool);
}
```

- [ ] **Step 1: Write tests for deterministic stepping, pause, and sleep-size time jumps.**

A simulation created twice with the same seed/profile and stepped with the same sequence must produce identical snapshot signatures. `set_paused(true)` followed by large `real_dt` must not advance biological time.

- [ ] **Step 2: Write a no-allocation hot-loop test harness.**

Provide a test-only counting allocator or benchmark instrumentation proving a warmed simulation step with fixed capacity performs zero heap allocations in the normal steady-state path.

- [ ] **Step 3: Run tests and verify failure.**

```bash
cargo test -p simulation clock state snapshot -- --nocapture
```

- [ ] **Step 4: Implement structure-of-arrays storage.**

Keep aligned vectors for ids, display ids, positions mm, velocities mm/s, headings, body size sample, gait phase, behavior state, timers, stable trait seed, and render-relevant state. Preallocate to configured capacity; spawn/despawn uses free-list indices rather than reallocating every frame.

- [ ] **Step 5: Implement a fixed biological tick with render interpolation state.**

Default high-level tick target is 30 Hz. `advance` accumulates real time, runs bounded fixed ticks, and records previous/current transforms for interpolation. Cap catch-up after long stalls; sleep/wake is handled as a lifecycle pause, not thousands of catch-up ticks.

- [ ] **Step 6: Verify deterministic snapshots and commit.**

```bash
cargo test -p simulation
cargo clippy -p simulation --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/simulation" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add deterministic fixed-step creature simulation"
```

---

### Task 7: Implement the spatial hash and coarse trail field

**Files:**
- Create: `$APP_ROOT/app/crates/simulation/src/{spatial.rs,trails.rs}`
- Create: `$APP_ROOT/app/crates/simulation/benches/spatial.rs`
- Modify: `$APP_ROOT/app/crates/simulation/src/state.rs`
- Test: unit + property tests

**Interfaces:**
- Produces:

```rust
pub struct SpatialHash { /* reusable buckets */ }
impl SpatialHash {
    pub fn rebuild(&mut self, positions: &[Vec2Mm], displays: &[DisplayId]);
    pub fn neighbors(&self, display: DisplayId, position: Vec2Mm, radius_mm: f32, out: &mut Vec<usize>);
}

pub struct TrailField { /* coarse per-display grid */ }
impl TrailField {
    pub fn sample(&self, display: DisplayId, position: Vec2Mm) -> TrailSample;
    pub fn deposit(&mut self, display: DisplayId, position: Vec2Mm, direction: glam::Vec2, amount: f32);
    pub fn decay_step(&mut self, dt_s: f32);
}
```

- [ ] **Step 1: Test spatial hash against brute force.**

Generate small random populations and assert the sorted neighbor set exactly matches brute-force radius checks.

- [ ] **Step 2: Test bounded trail decay and direction accumulation.**

Trail intensity must stay finite and within `[0, max_intensity]`; repeated decay monotonically reduces intensity when no deposits occur.

- [ ] **Step 3: Implement reusable buckets and scratch buffers.**

Bucket sizes should be derived from interaction radii, not screen pixels. Avoid per-query allocation by passing reusable output buffers owned by simulation workers.

- [ ] **Step 4: Implement amortized trail updates.**

The field operates in physical millimeters with a coarse cell size documented in code and profile/config. Large-population deposits may be batched and decay may update row/tiles incrementally as long as deterministic ordering remains defined.

- [ ] **Step 5: Add and run the Criterion spatial benchmark.**

The benchmark compares 500, 1,000, and 2,000 agents and records neighbor-query scaling. It must not perform a hidden brute-force path in the production measurement.

```bash
cargo bench -p simulation --bench spatial
```

- [ ] **Step 6: Verify and commit.**

```bash
cargo test -p simulation spatial trails
git add "$APP_ROOT/app/crates/simulation" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add spatial queries and trail field"
```

---

### Task 8: Implement research-backed ant behavior, locomotion, gait, encounters, edges, and spawn/exit

**Files:**
- Create: `$APP_ROOT/app/crates/simulation/src/{behavior.rs,locomotion.rs,spawn.rs}`
- Modify: `$APP_ROOT/app/crates/simulation/src/state.rs`
- Modify: `$APP_ROOT/app/crates/creature-profile/src/schema.rs` if the final profile needs additional measured distributions
- Test: unit + deterministic statistical tests

**Interfaces:**
- Produces `BehaviorState`, `AntTraits`, `AntPoseState`, and per-tick update functions used by `Simulation`.

```rust
pub enum BehaviorState { Explore, Transit, Probe, Pause, EdgeFollow, TrailFollow, Encounter, Avoid, Disturbance, Groom }

pub struct AntPoseState {
    pub gait_phase: f32,
    pub stride_frequency_hz: f32,
    pub turn_amount: f32,
    pub antenna_left: f32,
    pub antenna_right: f32,
    pub pose_blend: f32,
}
```

`Groom` transitions remain disabled unless the selected profile contains supporting evidence/parameters.

- [ ] **Step 1: Add tests proving movement comes from profile distributions, not fake waypoint/noise logic.**

With a tiny deterministic profile fixture, assert sampled run duration, angular velocity, pause duration, and preferred speed stay within profile ranges and are reproducible from seed. There must be no primary random-waypoint or Perlin-noise steering implementation.

- [ ] **Step 2: Add state-transition tests.**

Test examples: a paused ant eventually leaves Pause according to its sampled timer; a detected outer edge may turn/follow/exit but never hard-bounces; an encounter can produce no reaction/slow/antennate/follow/avoid; Realistic cursor-off environment cannot enter Disturbance from cursor motion.

- [ ] **Step 3: Implement persistent `AntTraits` sampled once at spawn.**

Traits include preferred speed, stride characteristic, turn tendency, pause tendency, direction persistence, edge-following tendency, trail sensitivity, encounter response weighting, body-size sample, antenna variation, and short behavior-memory parameters. Store them stably for the ant lifetime.

- [ ] **Step 4: Implement context-sensitive transition hazards and smooth locomotion intentions.**

Use probability/hazard evaluation at biological decision intervals; blend speed/curvature/heading over time. Do not rotate instantly to waypoints. Outer-edge behavior must investigate, turn, follow, or exit. A topology crossing transforms position/heading while preserving physical mm/s velocity.

- [ ] **Step 5: Couple gait and antennae to locomotion/behavior.**

`stride_frequency_hz` derives from translational speed and measured/derived stride relationships. When speed approaches zero the walking cycle settles rather than continuing. Normal ant locomotion uses alternating tripod phase where appropriate. Antenna targets use correlated asymmetric processes conditioned on Probe/Transit/Encounter/EdgeFollow, not two independent sine waves.

- [ ] **Step 6: Implement plausible spawn/exit dynamics.**

Population target changes adjust edge-entry/exit rates; normal spawns occur off-screen/edge regions. Only explicit developer `spawn_now` bypasses that rule.

- [ ] **Step 7: Add deterministic statistical validation.**

Run at least 10,000 simulated samples and compare empirical min/max/mean/quantiles to profile constraints/tolerances. Store the test seed. This is not a claim that the simulation reproduces biology perfectly; it is a guard against silently drifting outside the approved profile.

- [ ] **Step 8: Verify and commit.**

```bash
cargo test -p simulation behavior locomotion spawn -- --nocapture
cargo test --workspace
git add "$APP_ROOT/app/crates/simulation" "$APP_ROOT/app/crates/creature-profile" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: implement evidence-driven ant behavior and locomotion"
```

---

### Task 9: Define compact render instances and initialize the shared wgpu renderer

**Files:**
- Create: `$APP_ROOT/app/crates/rendering/src/{lib.rs,instance.rs,renderer.rs,lod.rs}`
- Modify: `$APP_ROOT/app/crates/simulation/src/lib.rs`
- Test: rendering unit tests that do not require visible windows

**Interfaces:**
- Produces:

```rust
#[repr(C)]
#[derive(Clone, Copy, bytemuck::Pod, bytemuck::Zeroable)]
pub struct CreatureRenderInstance {
    pub position_px: [f32; 2],
    pub heading_rad: f32,
    pub length_px: f32,
    pub width_px: f32,
    pub gait_phase: f32,
    pub speed_norm: f32,
    pub turn_amount: f32,
    pub pose_blend: f32,
    pub antenna: [f32; 2],
    pub morphology_seed: u32,
    pub lod: u32,
    pub material: [f32; 4],
}

pub struct RenderFrame<'a> { pub instances: &'a [CreatureRenderInstance], pub interpolation_alpha: f32 }
```

`Simulation` gains `build_render_instances(&mut self, display: &DisplaySurface, calibration: &DisplayCalibration, alpha: f32) -> &[CreatureRenderInstance]` using a preallocated scratch vector.

- [ ] **Step 1: Write tests for instance packing and LOD hysteresis.**

Assert `CreatureRenderInstance` is `Pod`, finite, tightly bounded in size, and that LOD does not toggle repeatedly around the threshold when pixel coverage jitters inside hysteresis.

- [ ] **Step 2: Run tests to confirm failure.**

```bash
cargo test -p rendering
```

- [ ] **Step 3: Implement a renderer owning one shared device/queue and per-overlay surface state.**

Expose:

```rust
pub struct Renderer { /* instance buffers, pipelines, device, queue */ }
pub struct SurfaceRenderer { /* wgpu surface/config for one monitor */ }

impl Renderer {
    pub async fn new(adapter_hint: AdapterHint) -> Result<Self, RenderError>;
    pub fn create_surface(&mut self, source: SurfaceSource, size: PixelSize) -> Result<SurfaceRenderer, RenderError>;
    pub fn render(&mut self, surface: &mut SurfaceRenderer, frame: &RenderFrame<'_>) -> Result<RenderStats, RenderError>;
}
```

`SurfaceSource` is defined by Task 12 as a pair of raw display/window handles whose native overlay owner outlives the `wgpu::Surface`. `create_surface` may use `wgpu::SurfaceTargetUnsafe::RawHandle`; the unsafe block must document that lifetime invariant. Use one instanced draw path per LOD/material batch rather than per creature.

- [ ] **Step 4: Implement dynamic instance-buffer growth outside the hot loop.**

Allocate enough for at least 1,024 creatures initially, grow geometrically when needed, and reuse the buffer. Record upload bytes and active instance count in `RenderStats`.

- [ ] **Step 5: Configure each surface for the monitor rather than imposing a 60 Hz simulation clock.**

Choose a supported vsync/present mode that follows the compositor/display refresh; render interpolation is evaluated on every rendered frame so 120/144/165/240 Hz monitors remain smooth while biological ticks stay independent.

- [ ] **Step 6: Verify headless/offscreen initialization where supported and commit.**

```bash
cargo test -p rendering
cargo clippy -p rendering --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/rendering" "$APP_ROOT/app/crates/simulation" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add instanced wgpu rendering core"
```

---

### Task 10: Implement the procedural ant shader, subpixel appendages, material model, and LODs

**Files:**
- Create: `$APP_ROOT/app/crates/rendering/shaders/{ant.wgsl,composite.wgsl}`
- Modify: `$APP_ROOT/app/crates/rendering/src/{renderer.rs,lod.rs}`
- Test: shader compilation + offscreen image tests

**Interfaces:**
- Consumes: `CreatureRenderInstance`.
- Produces: Tiny, Standard, and Detailed ant LODs with stable screen-space coverage.

- [ ] **Step 1: Add shader compilation tests and offscreen 360-degree rotation fixtures.**

For headings `0..360` in fixed increments, render a 3 mm ant at representative 96, 144, 220+ PPI equivalents. Assert output has nonzero body coverage and appendage coverage does not disappear for long heading ranges. Store deterministic PNGs only for deliberately chosen golden cases.

- [ ] **Step 2: Implement the body as procedural three-mass silhouette plus narrow waist.**

Use a small instanced quad/bounds and analytic signed-distance/coverage math in WGSL to reconstruct head, thorax, abdomen and connections from stable morphology seed and physical pixel dimensions.

- [ ] **Step 3: Implement six segmented legs and two antennae with analytically anti-aliased capsules/strips.**

Coverage must remain stable through rotation. Joint targets derive from gait phase, turn amount, and pose state. Tiny LOD may simplify segment count but must preserve six-leg/paired-antenna read at usable pixel coverage.

- [ ] **Step 4: Implement restrained emissive-screen material behavior.**

Default output is dark/translucent coverage with subtle body variation and tiny bounded contact darkening. Explicitly do not add bloom, rim light, large shadow, AO halo, or forced bright outline. On dark backgrounds the ant may become less visible naturally.

- [ ] **Step 5: Implement LOD by physical pixel coverage with hysteresis.**

Tiny -> Standard -> Detailed thresholds must be centralized in `lod.rs`, configurable for validation, and based on projected physical size rather than camera distance.

- [ ] **Step 6: Run image tests and inspect 1:1 output, not only zoomed output.**

```bash
cargo test -p rendering shader image lod -- --nocapture
```

Record the exact validation image directory and manual 1:1 observations in `APP_EXECUTION.md`.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/rendering" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: render articulated ant impostors with stable subpixel limbs"
```

---

### Task 11: Build deterministic visual-validation scenes and developer diagnostics

**Files:**
- Create: `$APP_ROOT/app/crates/rendering/src/validation.rs`
- Create: `$APP_ROOT/app/crates/desktop-app/src/diagnostics.rs`
- Create: `$APP_ROOT/app/tests/visual/README.md`
- Test: rendering validation tests

**Interfaces:**
- Produces `ValidationSceneId`, `ValidationScene`, and `DiagnosticsSnapshot`.

- [ ] **Step 1: Define fixed scenes required by the spec.**

Include: stationary 2 mm, walking 3 mm, turning 4 mm, 100, 500, 1,000 creatures, heading sweep, gait sweep, bright background, dark background, high-contrast background, representative PPI values, Retina/high-DPI, and LOD transition sweep.

- [ ] **Step 2: Add a deterministic offscreen scene-render command.**

Use `clap` only for developer/CI command-line modes; normal startup with no args remains silent utility launch. The binary must support:

```bash
cargo run -p desktop-app --release -- --render-validation all --output target/validation
```

It writes images plus JSON metadata containing profile version, app commit, scene id, effective PPI, ant physical size, render backend, and image hash.

- [ ] **Step 3: Add developer diagnostic data contracts.**

`DiagnosticsSnapshot` includes creature count, rendered/culled count, simulation ms, behavior ms, spatial ms, trails ms, render prep ms, GPU ms when available, upload bytes, LOD counts, memory estimate, dropped simulation ticks, and current seed.

- [ ] **Step 4: Verify deterministic metadata/images within documented backend tolerances.**

Do not require bit-identical pixels across Metal and D3D if backend rasterization differs. Golden-image thresholds must be backend-aware and narrow enough to catch missing limbs/LOD regressions.

- [ ] **Step 5: Commit.**

```bash
cargo test -p rendering -p desktop-app
cargo run -p desktop-app --release -- --render-validation core --output target/validation-core
git add "$APP_ROOT/app/crates/rendering" "$APP_ROOT/app/crates/desktop-app" "$APP_ROOT/app/tests/visual" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "test: add deterministic visual validation harness"
```

---

### Task 12: Define the platform contract and fail-safe overlay state machine

**Files:**
- Create: `$APP_ROOT/app/crates/platform-api/src/{lib.rs,overlay.rs,event.rs,app_identity.rs}`
- Test: platform-api state-machine tests

**Interfaces:**
- Produces:

```rust
pub enum OverlaySafetyState { Created, Transparent, NonActivating, ClickThrough, SafeToShow, HiddenUnsafe }
pub enum PlatformEvent { DisplaysChanged, Suspend, Resume, ForegroundAppChanged(Option<AppIdentity>), QuitRequested }
pub struct AppIdentity { pub stable_id: String, pub display_name: String }

#[derive(Clone, Copy, Debug)]
pub struct SurfaceSource {
    pub raw_display_handle: raw_window_handle::RawDisplayHandle,
    pub raw_window_handle: raw_window_handle::RawWindowHandle,
}

pub trait OverlayWindow {
    fn display_id(&self) -> DisplayId;
    fn safety_state(&self) -> OverlaySafetyState;
    fn verify_input_passthrough(&self) -> Result<(), PlatformError>;
    fn set_visible(&mut self, visible: bool) -> Result<(), PlatformError>;
    fn surface_source(&self) -> Result<SurfaceSource, PlatformError>;
}

pub trait PlatformAdapter {
    fn enumerate_displays(&self) -> Result<Vec<DisplaySurface>, PlatformError>;
    fn create_overlay(&mut self, display: &DisplaySurface) -> Result<Box<dyn OverlayWindow>, PlatformError>;
    fn register_panic_hotkey(&mut self, binding: &str) -> Result<(), PlatformError>;
    fn set_launch_at_login(&self, enabled: bool) -> Result<(), PlatformError>;
    fn foreground_app(&self) -> Result<Option<AppIdentity>, PlatformError>;
    fn recent_apps(&self) -> Result<Vec<AppIdentity>, PlatformError>;
    fn poll_events(&mut self) -> Result<Vec<PlatformEvent>, PlatformError>;
}
```

The native object implementing `OverlayWindow` owns the underlying window and must outlive every `wgpu::Surface` created from its `SurfaceSource`.

- [ ] **Step 1: Test that unsafe overlays cannot be shown.**

A mock overlay in `Created`, `Transparent`, or `NonActivating` state must reject `set_visible(true)`. Only `SafeToShow` may become visible.

- [ ] **Step 2: Test panic-hide semantics independent of focus.**

Mock adapter must dispatch a global-hotkey event that hides all overlays without relying on an interactive overlay surface.

- [ ] **Step 3: Implement the state machine and common error taxonomy.**

Errors distinguish unsupported compositor behavior, safety-verification failure, native API error, hotkey conflict, and surface creation/device loss. Never downgrade a safety failure to a warning that still shows the window.

- [ ] **Step 4: Verify and commit.**

```bash
cargo test -p platform-api
cargo clippy -p platform-api --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/platform-api" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: define fail-safe native overlay contract"
```

---

### Task 13: Implement the macOS AppKit platform adapter

**Files:**
- Create: `$APP_ROOT/app/crates/platform-macos/src/{lib.rs,overlay.rs,status_item.rs,hotkey.rs,login.rs,display.rs}`
- Modify: target-specific dependencies in `$APP_ROOT/app/Cargo.toml`
- Test: macOS-only unit/smoke tests

**Interfaces:**
- Implements `PlatformAdapter` and `OverlayWindow` from Task 12.

- [ ] **Step 1: Add macOS-only tests for display discovery and safety-state transitions.**

Tests should verify Retina backing scale is separated from physical calibration, overlay configuration requests non-activating/click-through semantics, and failed native calls keep state below `SafeToShow`.

- [ ] **Step 2: Implement display enumeration.**

Use AppKit/CoreGraphics to obtain screen/frame, backing scale, pixel dimensions/refresh where available, physical size/EDID-like metadata where trustworthy, rotation, and stable fingerprint components. Flag uncertain physical size for manual calibration rather than inventing confidence.

- [ ] **Step 3: Implement one borderless transparent overlay per display.**

Use native AppKit APIs to create non-activating transparent windows, ignore mouse events, avoid normal Dock/Cmd-Tab presence, and select a normal permissible high window level. Apply native behavior through small documented `unsafe` FFI blocks only where required.

- [ ] **Step 4: Verify click-through before visibility.**

The adapter must perform its best available programmatic verification plus a manual smoke check documented in `tests/input-safety/README.md`. Failure means overlay remains hidden.

- [ ] **Step 5: Implement NSStatusItem menu, global panic hotkey, launch-at-login, Spaces/full-screen events, and sleep/wake notifications.**

Quick menu actions: Show/Hide, Pause/Resume, preset shortcuts, Settings, launch-at-login toggle, Quit.

- [ ] **Step 6: Run macOS validation in release mode.**

```bash
cargo test --workspace --release --target aarch64-apple-darwin
cargo build -p desktop-app --release --target aarch64-apple-darwin
```

On an actual Mac, manually verify clicks, double-clicks, drag, scroll, mouse move, keyboard focus to underlying app, Mission Control/Spaces, full-screen video, borderless game if available, hot-plug, and panic hotkey. Record exact results, including unsupported cases.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/platform-macos" "$APP_ROOT/app/Cargo.toml" "$APP_ROOT/app/tests/input-safety" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add safe macOS click-through overlay adapter"
```

---

### Task 14: Implement the Windows Win32/DWM platform adapter

**Files:**
- Create: `$APP_ROOT/app/crates/platform-windows/src/{lib.rs,overlay.rs,tray.rs,hotkey.rs,startup.rs,display.rs}`
- Modify: target-specific dependencies in `$APP_ROOT/app/Cargo.toml`
- Test: Windows-only unit/smoke tests

**Interfaces:**
- Implements the same `PlatformAdapter` and `OverlayWindow` contracts.

- [ ] **Step 1: Add Windows-only tests for DPI/display descriptors and safety-state transitions.**

Tests cover mixed per-monitor DPI, top-left origins including negative desktop coordinates, and refusal to show before hit-test/pass-through configuration is verified.

- [ ] **Step 2: Enable per-monitor-v2 DPI awareness before creating windows.**

Use Win32 DPI APIs so logical scaling never becomes biological physical size.

- [ ] **Step 3: Implement transparent tool-style overlay windows.**

Use Win32/DWM styles/hit testing to create borderless transparent non-taskbar windows, explicit input pass-through, no activation, and topmost behavior that does not constantly fight legitimate OS surfaces.

- [ ] **Step 4: Implement display enumeration and topology notifications.**

Collect pixel bounds, refresh, rotation, stable monitor identity, and trustworthy physical data where available. Handle `WM_DISPLAYCHANGE`, DPI change, session/suspend/resume notifications, and monitor hot-plug.

- [ ] **Step 5: Implement notification-area menu, global hotkey, and per-user launch-at-login.**

Use `Shell_NotifyIconW`, `RegisterHotKey`, and a per-user startup mechanism that does not require a service/kernel driver/admin rights.

- [ ] **Step 6: Run Windows validation in release mode.**

```powershell
cargo test --workspace --release --target x86_64-pc-windows-msvc
cargo build -p desktop-app --release --target x86_64-pc-windows-msvc
```

On actual Windows hardware, verify underlying input delivery, Alt-Tab absence, taskbar absence, full-screen video, borderless game if available, mixed-DPI multi-monitor, display changes, panic hotkey, and no console window.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/platform-windows" "$APP_ROOT/app/Cargo.toml" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add safe Windows click-through overlay adapter"
```

---

### Task 15: Build the app shell, settings UI, calibration UI, tray/menu actions, and lifecycle orchestration

**Files:**
- Create: `$APP_ROOT/app/crates/settings/src/{ui.rs,calibration_ui.rs}`
- Create: `$APP_ROOT/app/crates/desktop-app/src/{main.rs,app.rs,lifecycle.rs}`
- Modify: `$APP_ROOT/app/crates/desktop-app/Cargo.toml`
- Test: app-state tests with mock platform/renderer

**Interfaces:**
- Produces `DesktopApp`, `AppCommand`, and pure state reducers where practical.

```rust
pub enum AppCommand {
    ShowAll,
    HideAll,
    Pause,
    Resume,
    OpenSettings,
    SetPreset(PresetId),
    Recalibrate(DisplayId),
    Quit,
}
```

- [ ] **Step 1: Write app-state tests before the UI.**

Prove: Hide All immediately hides overlays and freezes simulation but is nonpersistent; Pause persists and remains paused across restart; closing Settings does not stop the infestation; first-run with low calibration confidence opens calibration; first-run with trusted calibration starts Realistic silently.

- [ ] **Step 2: Implement `DesktopApp` orchestration without platform details leaking into simulation.**

Main loop sequence: poll platform events -> apply lifecycle/config commands -> update display topology if dirty -> fixed-step simulation -> build per-display render instances -> render visible safe overlays -> draw settings window only when open -> collect diagnostics.

On Windows, `desktop-app/src/main.rs` must use `#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]` so a normal launch does not open a console. macOS packaging uses `LSUIElement` so the utility lives in the menu bar rather than as a normal Dock app.

- [ ] **Step 3: Implement compact egui settings.**

Primary surface contains only Enable, Preset, Population, displays, Launch at Login, Cursor Reaction, Continuous/Independent monitors, Safe Overlay Mode, Panic Hotkey, Secondary Creatures, Advanced. Advanced holds scale, seed, trail/population tuning, developer diagnostics, and research-backed range indicators. Secondary-creature controls are generated dynamically from qualified packaged `CreatureProfile` entries; do not compile fixed species names into the UI.

- [ ] **Step 4: Implement the 85.60 mm credit-card calibration UI.**

The reference rectangle must be rendered in physical pixels from the current candidate scale and update `DisplayCalibration` live; Save persists per display fingerprint. A deliberate creature-scale multiplier remains separate from calibration.

- [ ] **Step 5: Wire tray/menu actions to `AppCommand`.**

Do not duplicate business logic in tray callbacks. Platform menus dispatch commands into the same app reducer.

- [ ] **Step 6: Verify with mock adapters and both target builds.**

```bash
cargo test -p settings -p desktop-app
cargo check --workspace --all-targets
```

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/settings" "$APP_ROOT/app/crates/desktop-app" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add utility lifecycle settings and calibration UX"
```

---

### Task 16: Implement continuous multi-monitor migration, compatibility exclusions, hidden-state efficiency, and renderer recovery

**Files:**
- Create: `$APP_ROOT/app/crates/desktop-app/src/compatibility.rs`
- Modify: `$APP_ROOT/app/crates/desktop-app/src/{app.rs,lifecycle.rs}`
- Modify: `$APP_ROOT/app/crates/simulation/src/state.rs`
- Modify: `$APP_ROOT/app/crates/rendering/src/renderer.rs`
- Test: integration tests using mock displays/platform apps/device-loss injection

**Interfaces:**
- Produces `CompatibilityPolicy`, `VisibilityDecision`, and renderer recovery path.

- [ ] **Step 1: Write integration tests for monitor crossing and independent mode.**

A creature crossing from 220 PPI display A to 110 PPI display B preserves mm/s velocity and heading while pixel velocity changes. With independent mode, the same edge is exterior and cannot topology-cross.

- [ ] **Step 2: Write compatibility-policy tests.**

Per-app exclusion by stable app identity hides the relevant overlays. Safe mode may hide in full-screen/selected apps. Policy uses process/window metadata only; there is no screenshot/OCR path.

- [ ] **Step 3: Write hidden-state efficiency tests.**

When all overlays are panic-hidden, renderer submission count remains zero and biological ticks are frozen. When one monitor is hidden, its render count is zero without corrupting its population.

- [ ] **Step 4: Inject renderer/device-loss failures and test fail-safe recovery.**

Sequence must be: hide affected overlay -> keep utility controls alive -> recreate device/surface within bounded retries -> rebuild GPU resources -> re-run overlay safety verification -> show only on success. After final failure, remain hidden and surface diagnostic.

- [ ] **Step 5: Implement policies and recovery.**

Do not add retry loops that continuously thrash GPU/window APIs. Use bounded backoff/retry count and record the final native/wgpu error.

- [ ] **Step 6: Verify and commit.**

```bash
cargo test -p desktop-app -p simulation -p rendering
cargo clippy --workspace --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/desktop-app" "$APP_ROOT/app/crates/simulation" "$APP_ROOT/app/crates/rendering" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: handle topology compatibility and renderer recovery"
```

---

### Task 17: Qualify and integrate up to two secondary creatures without weakening ants

**Files:**
- Modify: `$APP_ROOT/app/assets/creature-profiles/runtime-profiles.bin` through the compiler, not by hand
- Modify: `$APP_ROOT/app/crates/creature-profile/src/schema.rs` only if a truly generic field is missing
- Modify: `$APP_ROOT/app/crates/simulation/src/*` with species-specific modules if required
- Modify: `$APP_ROOT/app/crates/rendering/src/*` with species-specific renderers if required
- Modify: `$APP_ROOT/app/crates/settings/src/ui.rs`
- Create: `$APP_ROOT/docs/SECONDARY_CREATURE_GATE.md`

**Interfaces:**
- Consumes: research candidates from the final Mega Pack.
- Produces: zero, one, or two qualified secondary runtime profiles/render paths.

- [ ] **Step 1: Score candidates using a factual gate, not preference.**

For each research candidate record: evidence depth, calibrated morphology, measured locomotion data, behavior data, usable/legal asset/data status, implementation complexity, expected screen-size readability, and predicted performance cost. Do not rank by aesthetics alone.

A candidate passes only if it has enough evidence to avoid implementing "an ant with a different skin" and does not require unrelated v1 systems such as full flight physics unless the research/design explicitly justifies them.

- [ ] **Step 2: Write `SECONDARY_CREATURE_GATE.md` with the pass/fail rationale.**

If no candidate passes, record that result and ship ants only. That is a successful gate, not a failure of the app plan.

- [ ] **Step 3: For each passing creature, write species-specific tests before implementation.**

Tests must cover its own locomotion invariants and renderer signature. A jumping springtail, for example, must have an explicit jump impulse/landing model from evidence rather than toggling an ant state.

- [ ] **Step 4: Implement passing creatures behind `CreatureKind` dispatch while retaining ant-specialized code.**

Do not refactor ant internals into a weak lowest-common-denominator interface merely to reduce lines of code.

- [ ] **Step 5: Verify ant benchmarks and visuals did not regress.**

```bash
cargo test --workspace
cargo run -p desktop-app --release -- --render-validation core --output target/validation-post-secondary
```

- [ ] **Step 6: Commit.**

```bash
git add "$APP_ROOT/docs/SECONDARY_CREATURE_GATE.md" "$APP_ROOT/app" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: gate and integrate evidence-qualified secondary creatures"
```

---

### Task 18: Add instrumentation, deterministic benchmarks, and optimize the 1,000+ creature target

**Files:**
- Create: `$APP_ROOT/app/crates/desktop-app/benches/{simulation.rs,stress.rs}`
- Modify: `$APP_ROOT/app/crates/desktop-app/src/{main.rs,diagnostics.rs}`
- Modify hot paths discovered by profiling
- Create/update: `$APP_ROOT/docs/PERFORMANCE.md`

**Interfaces:**
- Produces reproducible Criterion benches plus developer CLI benchmark mode and `BenchmarkReport` JSON containing median/p95/p99/worst metrics.

- [ ] **Step 1: Create four deterministic benchmark scenarios.**

Realistic = 50 ants; Heavy = 500; Required Stress = 1,000 with normal interactions/trails/render prep; Extreme = 2,000-5,000 stability. Use fixed seeds and the compiled runtime profile version.

- [ ] **Step 2: Implement benchmark CLI modes before measuring.**

Using the developer-only `clap` path established in Task 11, support:

```text
desktop-app --benchmark-scenario realistic --json <file>
desktop-app --benchmark-scenario heavy --json <file>
desktop-app --benchmark-scenario stress1000 --json <file>
desktop-app --benchmark-scenario extreme --json <file>
```

Normal no-argument app launch remains unaffected.

- [ ] **Step 3: Record the full metric set.**

Simulation, behavior, spatial, trails, render prep, GPU frame time when measurable, upload bytes, overlay overhead, total frame time, allocations, resident memory, LOD counts, dropped ticks. Compute median, p95, p99, worst.

- [ ] **Step 4: Establish pre-optimization release baselines.**

```bash
cargo bench --workspace
cargo run -p desktop-app --release -- --benchmark-scenario realistic --json target/bench-realistic.json
cargo run -p desktop-app --release -- --benchmark-scenario heavy --json target/bench-heavy.json
cargo run -p desktop-app --release -- --benchmark-scenario stress1000 --json target/bench-1000.json
cargo run -p desktop-app --release -- --benchmark-scenario extreme --json target/bench-extreme.json
```

Inspect the JSON. Do not claim performance from architecture alone.

- [ ] **Step 5: Profile the actual largest contributors before optimizing.**

Use platform-appropriate profiler/instruments available in the environment. Optimize measured bottlenecks only: allocations, cache layout, spatial bucket churn, trail update schedule, render instance upload, shader overdraw, unnecessary hidden-surface work, or decision cadence.

- [ ] **Step 6: Enforce hard hot-path invariants.**

Required: no all-pairs neighbor pass; no one-draw-call-per-creature; zero steady-state simulation allocations; no spawn/despawn hitch from vector growth after warm capacity; hidden overlays submit zero render work; paused state approaches idle.

- [ ] **Step 7: Re-run until the required 1,000-creature 60 FPS target passes on available ordinary modern reference hardware or a genuine hardware limitation is documented.**

60 FPS means frame budget <=16.67 ms with no recurring p99 spikes that make the result visibly stutter. If the available environment lacks representative GPU access, finish CPU/simulation optimization and record the GPU benchmark as an external hardware validation blocker rather than inventing a pass.

- [ ] **Step 8: Soak the extreme scenario.**

Run a long release-mode session (target at least 2 hours when environment permits) with hide/show, spawn/exit, preset changes, config saves, renderer recreation, and topology events. Record start/end memory and handle/resource counts. No unbounded growth or increasing frame-time trend.

- [ ] **Step 9: Write measured results to `PERFORMANCE.md` and commit.**

```bash
git add "$APP_ROOT/app" "$APP_ROOT/docs/PERFORMANCE.md" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "perf: validate and optimize thousand-creature workloads"
```

---

### Task 19: Add macOS/Windows packaging and project-specific CI/release workflows

**Files:**
- Create: `$APP_ROOT/app/packaging/macos/{Info.plist,build-dmg.sh}`
- Create: `$APP_ROOT/app/packaging/windows/wix/Product.wxs`
- Create: `$APP_ROOT/app/packaging/windows/build-msi.ps1`
- Create/modify project-specific workflows under `.github/workflows/` on the selected project lineage
- Create: `$APP_ROOT/docs/RELEASE.md`

**Interfaces:**
- Produces: macOS `.app` + `.dmg`, Windows GUI binary + `.msi` (or documented equivalent if toolchain constraints require), CI validation, checksums, optional signing when secrets exist.

- [ ] **Step 1: Add packaging smoke checks before release scripting.**

macOS smoke check asserts the `.app` has executable, `Info.plist`, packaged profiles/presets/shaders, LSUIElement/menu-bar behavior, and no unnecessary entitlements. Windows smoke check asserts GUI subsystem/no console, packaged assets, clean install/uninstall metadata, and no service/driver requirement.

- [ ] **Step 2: Implement macOS bundling.**

Primary build is Apple Silicon. Add x86_64/Universal 2 only if all dependencies compile reasonably. `build-dmg.sh` must work unsigned/ad-hoc for development; if signing identity/notarization credentials are present, use them, otherwise emit a clear unsigned-artifact note and continue.

- [ ] **Step 3: Implement Windows installer.**

Build x64 MSVC release binary and conventional per-user installer. Install/uninstall must not require a kernel component. Code signing is conditional on credentials; unsigned test MSI remains a valid build artifact.

- [ ] **Step 4: Add CI.**

Project CI must run at minimum:

```text
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
profile compiler compile + verify
macOS release build
Windows release build
```

Add separate/manual or nightly jobs for renderer validation and benchmarks where hosted GPU limitations make ordinary CI unreliable.

- [ ] **Step 5: Add release workflow.**

Tag release must: clean checkout -> compile profiles -> verify profiles -> full tests -> release builds -> packaging -> signing/notarization when credentials exist -> SHA-256 checksums -> upload artifacts. App version, profile bundle version, and profile schema version must be printed separately.

- [ ] **Step 6: Locally exercise packaging as far as the current host permits and inspect produced contents.**

Do not mark the non-host platform package verified merely because YAML exists; use cross-platform CI or actual target hardware for that evidence.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/packaging" "$APP_ROOT/docs/RELEASE.md" .github/workflows "$APP_ROOT/APP_EXECUTION.md"
git commit -m "build: package and validate macOS and Windows releases"
```

---

### Task 20: Finish documentation, full test matrix, input safety, final release verification, and handoff

**Files:**
- Create/update: `$APP_ROOT/README.md`
- Create/update: `$APP_ROOT/docs/{ARCHITECTURE.md,PLATFORM_SUPPORT.md,BIOLOGY_PROFILE_FORMAT.md,TESTING.md,PERFORMANCE.md,RELEASE.md}`
- Update: `$APP_ROOT/APP_EXECUTION.md`
- No unrelated source refactors in this task unless verification exposes a bug.

**Interfaces:**
- Produces the final evidence-backed completion record and maintainable project documentation.

- [ ] **Step 1: Write documentation from the actual implementation, not the old plan wording.**

`ARCHITECTURE.md` names crate boundaries/dependency direction. `PLATFORM_SUPPORT.md` distinguishes verified, best-effort, unsupported/protected compositor cases. `BIOLOGY_PROFILE_FORMAT.md` documents source-to-runtime schema/provenance. `TESTING.md` contains exact automated + manual matrices. `PERFORMANCE.md` contains measured numbers and hardware/backend details. `RELEASE.md` contains reproducible packaging/signing steps.

- [ ] **Step 2: Run the full repository/project verification from a clean working tree state.**

At minimum:

```bash
cd "$APP_ROOT/app"
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --release
cargo check --workspace --all-targets
cargo run -p profile-compiler --release -- compile --input "<resolved-mega-pack-root>" --output target/final-profiles.bin --report target/final-profiles.json
cargo run -p profile-compiler --release -- verify --bundle target/final-profiles.bin
cargo run -p desktop-app --release -- --render-validation all --output target/final-validation
cargo run -p desktop-app --release -- --benchmark-scenario stress1000 --json target/final-bench-1000.json
```

Then run the workspace-level coordination tests required by `AGENTS.md` from repo root:

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

- [ ] **Step 3: Execute the platform input-safety release blocker on each available target.**

With overlay active, underlying test app must receive click, double-click, drag, scroll, mouse movement, and keyboard input. Also verify panic hotkey while another app/game has focus. A target failing this test is not releasable; fix it and rerun.

- [ ] **Step 4: Inspect final release-mode visual output at calibrated 1:1 physical scale.**

Check 2/3/4 mm sizes, 360-degree headings, gait/antenna coupling, bright/dark content, LOD transitions, high refresh where hardware allows, and absence of major shimmer/disappearing limbs/cartoon shadow/outline. Record concrete observations, not "looks good" alone.

- [ ] **Step 5: Inspect artifacts, not just build exit codes.**

Open/list the `.app`, `.dmg`, Windows binary, and installer contents. Confirm runtime profiles, presets, shaders, icons/metadata are present and executable launch behavior is correct. Record signatures/notarization status honestly.

- [ ] **Step 6: Scan for unfinished implementation markers and accidental policy violations.**

Run targeted searches:

```bash
rg -n "TODO|TBD|FIXME|unimplemented!\(|todo!\(|panic!\(\"not implemented" "$APP_ROOT/app" "$APP_ROOT/docs"
rg -n "screen.?capture|OCR|inject|hook DirectX|kernel driver|telemetry|analytics" "$APP_ROOT/app"
```

Every match must be reviewed. Remove unfinished markers from required paths or document truly intentional future/non-v1 notes in docs rather than executable code.

- [ ] **Step 7: Compare the finished implementation against every section of the approved design spec.**

Create a final checklist in `APP_EXECUTION.md` mapping design sections 1-16 to concrete code/tests/docs/artifacts. Any missing required item reopens the relevant earlier task; implement/fix it before continuing.

- [ ] **Step 8: Re-run all affected tests after any final fixes.**

No completion claim based on stale pre-fix output.

- [ ] **Step 9: Commit final docs/verification.**

```bash
git add "$APP_ROOT/README.md" "$APP_ROOT/docs" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "docs: record verified insect desktop app release"
```

- [ ] **Step 10: Record coordination completion, release scopes, and close the agent session.**

Use `agentctl.py event` to record exact verification commands/results, `task-state ... completed` only if all available-environment acceptance gates pass, then release every claimed scope and mark the one-shot agent offline/retired according to repository policy. If a genuine external blocker remains, set the task to blocked with everything completed, blocker details, exact artifact/commit paths, and next action; still release scopes.

- [ ] **Step 11: Final report to the user.**

Report: branch + final SHA; what is implemented; automated test counts/results; measured 1,000-creature performance and reference hardware; macOS/Windows manual validation status; artifact paths; secondary-creature gate result; known OS limitations; signing/notarization status; any genuine remaining blocker. Do not use "complete" for an unverified target.

---

# Self-review coverage record

Before handing this plan to the user, the author checked it against the approved spec:

- Spec Sections 1-4 (purpose, locked decisions, repo rule, architecture): Tasks 1-3 plus Global Constraints.
- Section 5 (ant behavior): Tasks 6-8.
- Section 6 (rendering): Tasks 9-11.
- Section 7 (overlay/platform behavior): Tasks 12-16.
- Section 8 (settings/UX): Tasks 4, 5, 15, 16.
- Sections 9-10 (performance/testing): Tasks 6-11, 16, 18, 20.
- Sections 11-12 (packaging/CI/versioning): Tasks 2, 3, 19.
- Section 13 (documentation): Tasks 17-20.
- Section 14 (one-shot execution): One-shot Astra invocation contract + Tasks 1-20 continuous execution rule.
- Section 15 (definition of done): Task 20 + Definition of Done below.
- Section 16 (non-goals): Global Constraints + Non-goals reminder below.

Type/interface consistency was checked after defining `SurfaceSource`, benchmark locations, compiler subcommands, settings/profile dependencies, and developer CLI modes. No task intentionally references an undefined neighboring interface.

# Definition of Done

The one-shot execution is finished only when, as far as the available environment permits, all of the following are evidenced in `APP_EXECUTION.md` and the repository:

- macOS menu-bar utility launches without a terminal and Windows tray utility launches without a console.
- Transparent overlays render correctly and normal input passes through.
- Global panic hide works independently of overlay focus.
- True physical creature size uses trustworthy display metadata or per-display manual calibration.
- Runtime profiles are compiled deterministically from the Mega Pack with provenance; no arbitrary missing biology is invented.
- Ant behavior is deterministic in tests, trajectory/profile bounded, and not primarily random-waypoint/Perlin-noise movement.
- Gait and antennae follow locomotion/behavior; stopping actually settles walking motion.
- Independent and continuous multi-monitor modes work and preserve physical speed across density changes.
- Presets/settings/config migration/corruption recovery work and Realistic keeps cursor reaction off.
- Maximum overlay mode plus optional compatibility/per-app exclusions work without process injection or screen capture.
- 1,000-creature required stress workload meets the defined 60 FPS reference target on available representative hardware, or the unavailable hardware gate is explicitly documented after all measurable optimization work is complete.
- High-refresh interpolation is smooth on available 120/144/165/240 Hz hardware.
- Visual validation shows no major LOD popping, persistent subpixel-leg disappearance/shimmer, giant game-like shadows, or forced glow outlines.
- Panic-hidden overlays submit no render work and paused state approaches idle.
- Renderer/device-loss failure cannot leave an input-trapping/opaque topmost window.
- Soak/stress testing shows no unbounded memory/handle/GPU-resource growth or simulation degradation.
- Secondary creatures are included only if their research/quality gate passes; ants remain uncompromised.
- Installable/testable macOS and Windows artifacts are produced as far as target toolchains/hardware permit; missing signing credentials do not block unsigned artifacts.
- CI, release workflows, checksums, and separate app/profile/schema versioning exist.
- `README.md`, `ARCHITECTURE.md`, `PLATFORM_SUPPORT.md`, `BIOLOGY_PROFILE_FORMAT.md`, `PERFORMANCE.md`, `RELEASE.md`, and `TESTING.md` reflect actual verified behavior.
- Final release-mode test, benchmark, visual, packaging, and platform outputs are inspected after the last code change before any completion claim.

# Non-goals reminder

Do not spend the one-shot request adding accounts, cloud sync, analytics, mandatory auto-update, app-store integration, game injection, UI/content understanding, elaborate feeding/health gameplay, per-ant rigid-body/3D physics, Linux release work, or weakly evidenced secondary species. Those are outside v1 unless the user explicitly changes the approved design.
