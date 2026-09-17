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

# Final file and crate map

The executor grows the workspace task-by-task into this final structure. Do not pre-create empty placeholder crates; each task creates a crate when it has a real tested responsibility.

```text
$APP_ROOT/app/
├── Cargo.toml
├── Cargo.lock
├── rust-toolchain.toml
├── crates/
│   ├── creature-profile/
│   │   └── src/{lib.rs,schema.rs,validate.rs}
│   ├── display-model/
│   │   └── src/{lib.rs,units.rs,display.rs,calibration.rs,topology.rs}
│   ├── settings/
│   │   └── src/{lib.rs,config.rs,migrate.rs,preset.rs,ui.rs,calibration_ui.rs}
│   ├── simulation/
│   │   ├── src/{lib.rs,clock.rs,state.rs,spatial.rs,trails.rs,behavior.rs,locomotion.rs,spawn.rs,snapshot.rs}
│   │   └── benches/spatial.rs
│   ├── rendering/
│   │   ├── src/{lib.rs,instance.rs,renderer.rs,lod.rs,validation.rs}
│   │   ├── shaders/{ant.wgsl,composite.wgsl}
│   │   └── examples/validation.rs
│   ├── platform-api/
│   │   └── src/{lib.rs,overlay.rs,event.rs,app_identity.rs}
│   ├── platform-macos/
│   │   └── src/{lib.rs,overlay.rs,status_item.rs,hotkey.rs,login.rs,display.rs}
│   ├── platform-windows/
│   │   └── src/{lib.rs,overlay.rs,tray.rs,hotkey.rs,startup.rs,display.rs}
│   └── desktop-app/
│       ├── src/{main.rs,app.rs,lifecycle.rs,compatibility.rs,diagnostics.rs}
│       └── benches/{simulation.rs,stress.rs}
├── tools/
│   └── profile-compiler/
│       ├── src/main.rs
│       └── tests/compiler.rs
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

Project docs:

```text
$APP_ROOT/README.md
$APP_ROOT/docs/ARCHITECTURE.md
$APP_ROOT/docs/PLATFORM_SUPPORT.md
$APP_ROOT/docs/BIOLOGY_PROFILE_FORMAT.md
$APP_ROOT/docs/PERFORMANCE.md
$APP_ROOT/docs/RELEASE.md
$APP_ROOT/docs/TESTING.md
$APP_ROOT/docs/SECONDARY_CREATURE_GATE.md
```

Dependency direction is one-way:

```text
creature-profile   display-model   settings
       \              |             /
        \             |            /
             simulation
                 |
             rendering <---- platform-api
                 ^              ^
                 |              |
             desktop-app ---- platform-{macos,windows}
```

`simulation` never imports `rendering` or a platform crate. `platform-api` never imports `rendering`. `rendering` may consume raw surface handles defined by `platform-api` after Task 12. `desktop-app` is the composition root.

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
- Produces: exact `APP_ROOT`, exact implementation branch, exclusive non-conflicting app/docs scopes, and durable execution checklist used by every later task.

- [ ] **Step 1: Refresh coordination state and inspect all insect-related work before editing.**

```bash
git fetch --all --prune
git switch main
git pull --ff-only
python agentctl.py agent-list
python agentctl.py task-list
```

Read the workspace contract, current insect tasks, active leases, events, handoffs, and candidate project branches. Do not infer newest/canonical from branch names.

- [ ] **Step 2: Resolve the implementation base using explicit evidence.**

For each candidate Mega Pack lineage record branch/ref, project root, head SHA, task intent, lease state, verification evidence, and whether the final derived profiles/pack are actually complete. Select the newest validated lineage by content + ancestry + intent + verification. If an unexpired lease still covers the same project root, do not begin implementation there.

- [ ] **Step 3: Register/claim the smallest safe app scopes and create the implementation branch.**

Substitute concrete values for every angle-bracket token below:

```bash
python agentctl.py register --id astra-insect-app-<unique> --provider openai --model astra --capability rust --capability macos --capability windows --capability graphics
python agentctl.py task-create --id build-insect-realism-desktop-app-<unique> --title "Build insect realism desktop app" --created-by astra-insect-app-<unique> --objective "Implement the approved desktop app spec end-to-end" --scope "<resolved-project>/app" --scope "<resolved-project>/docs" --accept "Approved spec definition-of-done passes" --priority high
python agentctl.py claim --task build-insect-realism-desktop-app-<unique> --scope "<resolved-project>/app" --agent astra-insect-app-<unique> --ttl 240 --intent "Implement the one-shot desktop app plan"
python agentctl.py claim --task build-insect-realism-desktop-app-<unique> --scope "<resolved-project>/docs" --agent astra-insect-app-<unique> --ttl 240 --intent "Document and verify the desktop app"
```

Claims must reach `main` before implementation starts.

```bash
git switch <resolved-project-base>
git pull --ff-only
git switch -c feat/insect-realism-desktop-app-<unique>
```

If that feature branch already exists with unrelated/incomplete work, choose a fresh unique branch rather than overwriting it.

- [ ] **Step 4: Create `APP_EXECUTION.md`.**

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
- [ ] Settings/presets
- [ ] Display/calibration model
- [ ] Simulation + spatial + trails
- [ ] Ant behavior/locomotion
- [ ] Renderer + visual validation
- [ ] Platform contract
- [ ] macOS adapter
- [ ] Windows adapter
- [ ] App shell/settings UI
- [ ] Multi-monitor/compatibility/recovery
- [ ] Secondary creature gate
- [ ] Performance/soak
- [ ] Packaging/CI
- [ ] Final release verification
```

- [ ] **Step 5: Commit the bootstrap decision and continue automatically.**

```bash
git add "$APP_ROOT/APP_EXECUTION.md"
git commit -m "chore: start insect desktop app execution"
```

---

### Task 2: Create the Rust workspace and shared domain foundations

**Files:**
- Create: `$APP_ROOT/app/Cargo.toml`
- Create: `$APP_ROOT/app/rust-toolchain.toml`
- Create: `$APP_ROOT/app/crates/creature-profile/Cargo.toml`
- Create: `$APP_ROOT/app/crates/creature-profile/src/{lib.rs,schema.rs,validate.rs}`
- Create: `$APP_ROOT/app/crates/display-model/Cargo.toml`
- Create: `$APP_ROOT/app/crates/display-model/src/{lib.rs,units.rs}`

**Interfaces:**
- Produces `Millimeters`, `Vec2Mm`, `CreatureId`, `DisplayId`, `CreatureKind`, `CreatureProfile`, `RuntimeProfileBundle`, `RangeF32`, `EvidenceRef`, `ProfileError`.

- [ ] **Step 1: Write failing unit tests for units and invalid biology.**

```rust
#[test]
fn millimeters_reject_negative_and_non_finite_values() {
    assert!(Millimeters::new(-1.0).is_err());
    assert!(Millimeters::new(f32::NAN).is_err());
    assert_eq!(Millimeters::new(3.0).unwrap().get(), 3.0);
}

#[test]
fn vec2_mm_rejects_non_finite_components() {
    assert!(Vec2Mm::new(f32::NAN, 1.0).is_err());
}
```

Add a creature-profile test constructing a negative body-length range and requiring `ProfileError::InvalidRange`.

- [ ] **Step 2: Create the workspace root with glob members so later real crates join automatically.**

Use:

```toml
[workspace]
resolver = "2"
members = ["crates/*", "tools/*"]

[workspace.package]
version = "0.1.0"
edition = "2024"
license = "MIT"
```

Add workspace dependencies for `serde`, `serde_json`, `toml`, `thiserror`, `glam`, `rand_chacha`, `rand_core`, `bytemuck`, `wgpu`, `winit`, `egui`, `egui-wgpu`, `tracing`, `tracing-subscriber`, `postcard`, `directories`, `proptest`, `criterion`, `image`, `pollster`, `raw-window-handle`, `clap`, `sha2`, and `tempfile`. Pin compatible versions, commit `Cargo.lock`, and change them intentionally thereafter.

- [ ] **Step 3: Run tests to confirm failure, then implement domain types.**

```bash
cd "$APP_ROOT/app"
cargo test -p display-model
cargo test -p creature-profile
```

Required contracts:

```rust
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
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, serde::Serialize, serde::Deserialize)]
pub struct CreatureId(pub u64);
#[derive(Clone, Debug, PartialEq, Eq, Hash, serde::Serialize, serde::Deserialize)]
pub struct CreatureKind(pub String);
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct EvidenceRef { pub source_id: String, pub field: String }
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
impl RuntimeProfileBundle { pub fn validate(&self) -> Result<(), ProfileError>; }
```

Schema version starts at `1`. Validation rejects non-finite/reversed/negative ranges, duplicate creature ids, unsupported schema versions, missing evidence for required biology, and bundles without an ant profile.

- [ ] **Step 4: Verify and commit.**

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
git add "$APP_ROOT/app" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "build: establish insect app Rust foundations"
```

---

### Task 3: Compile the Mega Pack into deterministic runtime profiles

**Files:**
- Create: `$APP_ROOT/app/tools/profile-compiler/Cargo.toml`
- Create: `$APP_ROOT/app/tools/profile-compiler/src/main.rs`
- Create: `$APP_ROOT/app/tools/profile-compiler/tests/compiler.rs`
- Modify: `$APP_ROOT/app/crates/creature-profile/src/{schema.rs,validate.rs}` as required by actual final evidence fields
- Create: `$APP_ROOT/app/assets/creature-profiles/README.md`
- Create/generated: `$APP_ROOT/app/assets/creature-profiles/runtime-profiles.bin`
- Create/generated: `$APP_ROOT/app/assets/creature-profiles/runtime-profiles.report.json`

**Interfaces:**

```text
profile-compiler compile --input <mega-pack-root> --output <file> --report <json>
profile-compiler verify --bundle <file>
```

- [ ] **Step 1: Inspect the actual resolved final Mega Pack app-consumable outputs.**

Write a source mapping table to `assets/creature-profiles/README.md`: source file, source field, unit, runtime field, evidence id, transformation. Do not guess source filenames from this plan. If required ant anatomy/trajectory parameters are absent, record a real blocker instead of inventing values.

- [ ] **Step 2: Write deterministic compiler tests.**

```rust
#[test]
fn compile_is_byte_deterministic() {
    let a = compile_fixture("valid").unwrap();
    let b = compile_fixture("valid").unwrap();
    assert_eq!(a.bytes, b.bytes);
}

#[test]
fn invalid_physical_units_are_rejected() {
    assert!(compile_fixture("negative_body_length").is_err());
}
```

Also assert the JSON report lists the exact evidence ids and input files used.

- [ ] **Step 3: Run tests and verify failure.**

```bash
cargo test -p profile-compiler --test compiler -- --nocapture
```

- [ ] **Step 4: Implement parse -> normalize units -> validate -> serialize.**

`compile` outputs `postcard` bytes plus a JSON report containing schema version, research/profile version, creature ids, source files, evidence ids, and SHA-256. `verify` decodes, validates, recomputes the digest, and exits nonzero on any error. Never silently clamp bad measurements.

- [ ] **Step 5: Compile and verify the real pack.**

```bash
cargo run -p profile-compiler --release -- compile --input "<resolved-mega-pack-root>" --output assets/creature-profiles/runtime-profiles.bin --report assets/creature-profiles/runtime-profiles.report.json
cargo run -p profile-compiler --release -- verify --bundle assets/creature-profiles/runtime-profiles.bin
cargo test -p creature-profile -p profile-compiler
```

Secondary profiles may remain packaged but are not enabled until Task 17.

- [ ] **Step 6: Commit.**

```bash
git add "$APP_ROOT/app/tools/profile-compiler" "$APP_ROOT/app/crates/creature-profile" "$APP_ROOT/app/assets/creature-profiles" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: compile research evidence into runtime profiles"
```

---

### Task 4: Implement versioned settings and data-driven presets

**Files:**
- Create: `$APP_ROOT/app/crates/settings/Cargo.toml`
- Create: `$APP_ROOT/app/crates/settings/src/{lib.rs,config.rs,migrate.rs,preset.rs}`
- Create: `$APP_ROOT/app/assets/presets/{realistic.toml,light.toml,heavy.toml,nightmare.toml}`

**Interfaces:**

```rust
pub enum PresetId { Realistic, Light, Heavy, Nightmare, Custom }
pub enum AppExclusionMode { Hide, Compatibility }
pub struct AppExclusion { pub stable_id: String, pub mode: AppExclusionMode }

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

- [ ] **Step 1: Write failing tests for defaults, preset behavior, atomic save, migration, and corrupt recovery.**

Prove Realistic has cursor reaction OFF; Nightmare changes population pressure rather than biological speed; changing a preset-controlled value yields Custom; corrupt config is preserved as `.corrupt-<timestamp>` before defaults are restored.

- [ ] **Step 2: Implement preset files as data.**

Preset data may alter target population, spawn pressure, trail persistence multiplier, activity weighting, and secondary composition. It must not replace research profile speed/morphology distributions.

- [ ] **Step 3: Implement atomic persistence and explicit migrations.**

Write sibling temp -> `sync_all` -> atomic rename where supported -> parent sync where practical. Unknown future schema versions fail safely rather than being overwritten.

- [ ] **Step 4: Verify and commit.**

```bash
cargo test -p settings
cargo clippy -p settings --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/settings" "$APP_ROOT/app/assets/presets" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add versioned settings and infestation presets"
```

---

### Task 5: Build physical display calibration and topology

**Files:**
- Create: `$APP_ROOT/app/crates/display-model/src/{display.rs,calibration.rs,topology.rs}`
- Modify: `$APP_ROOT/app/crates/display-model/src/lib.rs`

**Interfaces:**

```rust
pub struct PixelSize { pub width: u32, pub height: u32 }
pub struct PhysicalSizeMm { pub width: f32, pub height: f32 }
pub struct DisplayFingerprint(pub String);
pub enum CalibrationConfidence { TrustedMetadata, Manual, NeedsManual }
pub struct DisplayCalibration { pub mm_per_physical_px: f32, pub confidence: CalibrationConfidence }

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

pub struct DisplayTopology { /* private graph */ }
impl DisplaySurface {
    pub fn mm_to_physical_px(&self, point: Vec2Mm, calibration: &DisplayCalibration) -> glam::Vec2;
}
impl DisplayTopology {
    pub fn rebuild(displays: &[DisplaySurface], calibrations: &std::collections::HashMap<DisplayId, DisplayCalibration>) -> Result<Self, TopologyError>;
    pub fn crossing(&self, display: DisplayId, point: Vec2Mm, velocity_mm_s: glam::Vec2) -> Option<SurfaceCrossing>;
}
```

- [ ] **Step 1: Write mixed-density unit tests.**

The same 3.0 mm body must map to different pixel lengths on two densities while remaining 3.0 mm physically. A continuous crossing preserves mm/s velocity and heading.

- [ ] **Step 2: Add `proptest` monitor-layout invariants.**

Finite positive display/calibration inputs must yield finite transforms; random sane layouts must not create NaN overlap intervals or invalid self-connections.

- [ ] **Step 3: Implement calibration priority.**

Trusted physical metadata -> saved per-fingerprint manual calibration -> `NeedsManual`. Do not infer high-confidence PPI from logical UI scale. Manual calibration uses the 85.60 mm ISO/IEC ID-1 credit-card width.

- [ ] **Step 4: Implement physical overlap topology.**

Continuous connections exist only across adjacent OS edges with physically overlapping calibrated segments. Independent mode disables connections.

- [ ] **Step 5: Verify and commit.**

```bash
cargo test -p display-model
cargo clippy -p display-model --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/display-model" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: model calibrated physical display topology"
```

---

### Task 6: Create the deterministic fixed-step simulation and cache-friendly storage

**Files:**
- Create: `$APP_ROOT/app/crates/simulation/Cargo.toml`
- Create: `$APP_ROOT/app/crates/simulation/src/{lib.rs,clock.rs,state.rs,snapshot.rs}`

**Interfaces:**

```rust
pub struct CursorDisturbance { pub display: DisplayId, pub position_mm: Vec2Mm, pub velocity_mm_s: glam::Vec2, pub strength: f32 }
pub struct SimulationConfig { pub tick_hz: u32, pub capacity: usize, pub seed: u64 }
pub struct EnvironmentSnapshot<'a> { pub topology: &'a DisplayTopology, pub cursor: Option<CursorDisturbance> }

pub struct Simulation { /* SoA buffers + clock + RNG + reusable scratch */ }
impl Simulation {
    pub fn new(config: SimulationConfig, profiles: std::sync::Arc<RuntimeProfileBundle>) -> Result<Self, SimulationError>;
    pub fn advance(&mut self, real_dt: std::time::Duration, env: &EnvironmentSnapshot<'_>) -> Result<AdvanceReport, SimulationError>;
    pub fn snapshot(&self) -> SimulationSnapshot;
    pub fn set_target_population(&mut self, target: u32);
    pub fn set_paused(&mut self, paused: bool);
}
```

- [ ] **Step 1: Write deterministic/pause tests and a steady-state allocation test.**

Two simulations with identical seed/profile/time inputs produce identical snapshot signatures. Paused simulation does not advance under a large `real_dt`. A warmed fixed-capacity tick performs zero heap allocations in its steady-state hot path.

- [ ] **Step 2: Run tests and confirm failure.**

```bash
cargo test -p simulation -- --nocapture
```

- [ ] **Step 3: Implement structure-of-arrays storage.**

Aligned vectors hold ids, display ids, previous/current positions mm, velocities mm/s, headings, morphology samples, behavior state/timers, gait/pose fields, stable trait seed, and active/free indices. Preallocate capacity and use a free list for spawn/despawn.

- [ ] **Step 4: Implement a default 30 Hz biological fixed step with interpolation state.**

`advance` accumulates real time, runs bounded fixed ticks, and exposes interpolation alpha. Cap catch-up after stalls. Lifecycle suspend/resume pauses biological time rather than simulating hours of sleep.

- [ ] **Step 5: Verify and commit.**

```bash
cargo test -p simulation
cargo clippy -p simulation --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/simulation" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add deterministic fixed-step creature simulation"
```

---

### Task 7: Implement spatial hash and coarse trail field

**Files:**
- Create: `$APP_ROOT/app/crates/simulation/src/{spatial.rs,trails.rs}`
- Create: `$APP_ROOT/app/crates/simulation/benches/spatial.rs`
- Modify: `$APP_ROOT/app/crates/simulation/Cargo.toml`

**Interfaces:**

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

- [ ] **Step 1: Test spatial results against brute force and trail bounds.**

Property-test small populations; sorted neighbor sets must match brute force exactly. Trail intensity stays finite/bounded and decays monotonically without deposits.

- [ ] **Step 2: Implement reusable buckets/scratch buffers.**

Bucket size derives from interaction radius in millimeters. Callers reuse neighbor output capacity; production queries do not allocate per creature.

- [ ] **Step 3: Implement deterministic amortizable trail updates.**

Use a coarse physical-mm grid, directional accumulation, bounded intensity, and deterministic batching/tile order.

- [ ] **Step 4: Add Criterion bench metadata.**

Add:

```toml
[[bench]]
name = "spatial"
harness = false
```

Benchmark 500, 1,000, and 2,000 agents.

- [ ] **Step 5: Verify and commit.**

```bash
cargo test -p simulation
cargo bench -p simulation --bench spatial
git add "$APP_ROOT/app/crates/simulation" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add spatial queries and trail field"
```

---

### Task 8: Implement evidence-driven ant behavior, locomotion, gait, antennae, encounters, edges, and spawn/exit

**Files:**
- Create: `$APP_ROOT/app/crates/simulation/src/{behavior.rs,locomotion.rs,spawn.rs}`
- Modify: `$APP_ROOT/app/crates/simulation/src/state.rs`
- Modify: `$APP_ROOT/app/crates/creature-profile/src/schema.rs` only for evidence-backed fields needed by the final pack

**Interfaces:**

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

#[derive(Clone, Copy, Debug)]
pub struct VisualCreatureState {
    pub id: CreatureId,
    pub kind_index: u16,
    pub display: DisplayId,
    pub previous_position_mm: Vec2Mm,
    pub position_mm: Vec2Mm,
    pub heading_rad: f32,
    pub body_length_mm: f32,
    pub body_width_mm: f32,
    pub gait_phase: f32,
    pub speed_norm: f32,
    pub turn_amount: f32,
    pub pose_blend: f32,
    pub antenna: [f32; 2],
    pub morphology_seed: u32,
}

impl Simulation {
    pub fn visual_states(&self) -> &[VisualCreatureState];
}
```

`Groom` transitions remain disabled unless selected evidence supports them.

- [ ] **Step 1: Test that profile distributions drive motion.**

With a deterministic tiny profile, run duration, angular velocity, pause duration, and preferred speed must stay inside profile ranges and repeat from seed. Do not add random-waypoint or Perlin-noise steering as the primary locomotion model.

- [ ] **Step 2: Test state transitions and physical edges.**

Pause exits according to its timer; an outer edge may investigate/turn/follow/exit but never hard-bounces; encounters may no-op/slow/antennate/follow/avoid; Realistic cursor-off cannot enter Disturbance due to cursor movement.

- [ ] **Step 3: Implement stable `AntTraits` sampled once at spawn.**

Sample preferred speed, stride characteristic, turn tendency, pause tendency, direction persistence, edge-following tendency, trail sensitivity, encounter weighting, morphology, antenna variation, and short behavior-memory parameters from validated profiles.

- [ ] **Step 4: Implement context-sensitive transition hazards and smooth intentions.**

Evaluate expensive decisions at staggered biological intervals; blend velocity/curvature/heading instead of snapping to waypoints. Topology crossings preserve physical mm/s velocity.

- [ ] **Step 5: Couple gait and antennae to motion/behavior.**

Stride frequency derives from translational speed; near-zero speed settles the gait. Use alternating tripod coordination where supported. Antenna targets are correlated/asymmetric and conditioned on Probe/Transit/Encounter/EdgeFollow, not independent sine waves.

- [ ] **Step 6: Implement edge entry/exit population dynamics.**

Target population changes influence edge-entry/exit rates. Normal creatures do not materialize in the center; only explicit developer spawn-now may do so.

- [ ] **Step 7: Add deterministic statistical validation.**

At least 10,000 sampled runs compare empirical bounds/quantiles to profile constraints/tolerances. Store the seed.

- [ ] **Step 8: Verify and commit.**

```bash
cargo test -p simulation -- --nocapture
cargo test --workspace
git add "$APP_ROOT/app/crates/simulation" "$APP_ROOT/app/crates/creature-profile" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: implement evidence-driven ant behavior and locomotion"
```

---

### Task 9: Build the offscreen wgpu renderer and compact instance pipeline

**Files:**
- Create: `$APP_ROOT/app/crates/rendering/Cargo.toml`
- Create: `$APP_ROOT/app/crates/rendering/src/{lib.rs,instance.rs,renderer.rs,lod.rs}`

**Interfaces:**

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

pub struct InstanceBuilder { /* reusable output */ }
impl InstanceBuilder {
    pub fn build<'a>(&'a mut self, states: &[VisualCreatureState], display: &DisplaySurface, calibration: &DisplayCalibration, alpha: f32) -> &'a [CreatureRenderInstance];
}

pub struct Renderer { /* device, queue, pipelines, reusable instance buffers */ }
impl Renderer {
    pub async fn new_headless(adapter_hint: AdapterHint) -> Result<Self, RenderError>;
    pub fn render_offscreen(&mut self, target: &mut OffscreenTarget, frame: &[CreatureRenderInstance]) -> Result<RenderStats, RenderError>;
}
```

- [ ] **Step 1: Write instance packing, interpolation, and LOD-hysteresis tests.**

`CreatureRenderInstance` must be `Pod`, finite, compact, and generated without simulation importing rendering. Interpolation happens while converting previous/current mm positions to pixels. LOD must not flap around thresholds.

- [ ] **Step 2: Implement a headless/offscreen wgpu device and reusable instance buffers.**

Initial capacity >=1,024; geometric growth only outside steady-state hot paths. One instanced draw path per LOD/material batch, never per creature.

- [ ] **Step 3: Record render stats.**

At minimum: active instances, upload bytes, CPU render-prep duration, submitted draws, and GPU duration where timestamp-query support exists.

- [ ] **Step 4: Verify and commit.**

```bash
cargo test -p rendering
cargo clippy -p rendering --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/rendering" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add instanced wgpu rendering core"
```

---

### Task 10: Implement procedural ant shaders, stable subpixel appendages, material, and LOD

**Files:**
- Create: `$APP_ROOT/app/crates/rendering/shaders/{ant.wgsl,composite.wgsl}`
- Modify: `$APP_ROOT/app/crates/rendering/src/{renderer.rs,lod.rs}`

- [ ] **Step 1: Add WGSL compile tests and offscreen rotation fixtures.**

Render a 3 mm ant across headings and representative 96/144/220+ PPI equivalents. Assert body coverage remains nonzero and appendage coverage does not vanish over long angle ranges.

- [ ] **Step 2: Implement the ant body as a procedural three-mass silhouette with narrow waist.**

Reconstruct head, thorax, abdomen, and connections in WGSL from compact instance/morphology data.

- [ ] **Step 3: Implement six segmented legs and two antennae using analytically anti-aliased screen-space capsules/strips.**

Joint positions derive from gait phase, turn amount, pose blend, and antenna state. Tiny LOD may simplify geometry while preserving the visible insect silhouette.

- [ ] **Step 4: Implement restrained emissive-screen material response.**

Dark/translucent coverage + subtle stable body variation + tiny bounded contact darkening. No bloom, rim light, obvious drop shadow, AO halo, or forced bright outline.

- [ ] **Step 5: Implement physical-pixel-coverage LOD with hysteresis.**

Tiny -> Standard -> Detailed thresholds live centrally in `lod.rs`, not scattered through shaders.

- [ ] **Step 6: Verify and inspect 1:1 output.**

```bash
cargo test -p rendering -- --nocapture
```

Record actual validation image paths and observations in `APP_EXECUTION.md`.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/rendering" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: render articulated ant impostors with stable subpixel limbs"
```

---

### Task 11: Add deterministic visual-validation scenes

**Files:**
- Create: `$APP_ROOT/app/crates/rendering/src/validation.rs`
- Create: `$APP_ROOT/app/crates/rendering/examples/validation.rs`
- Create: `$APP_ROOT/app/tests/visual/README.md`

**Interfaces:**

```rust
pub enum ValidationSceneId { Ant2mmStatic, Ant3mmWalk, Ant4mmTurn, Population100, Population500, Population1000, HeadingSweep, GaitSweep, LodSweep }
pub struct ValidationScene { /* deterministic profile/display/background/seed */ }
```

- [ ] **Step 1: Define all required deterministic scenes.**

Include 2/3/4 mm ants, 100/500/1,000 populations, heading/gait sweeps, bright/dark/high-contrast backgrounds, representative PPIs, high-DPI, and LOD transitions.

- [ ] **Step 2: Implement a standalone rendering example so validation does not depend on the not-yet-built desktop shell.**

```bash
cargo run -p rendering --example validation --release -- --scene all --output target/validation
```

Use `clap` in the example. Emit PNGs plus JSON metadata: scene id, app/git commit if available, profile version, PPI, physical size, backend, image hash.

- [ ] **Step 3: Add backend-aware image checks.**

Do not demand bit-identical Metal/D3D pixels. Use narrow documented tolerances that still catch missing limbs, silhouette breakage, and LOD popping.

- [ ] **Step 4: Verify and commit.**

```bash
cargo test -p rendering
cargo run -p rendering --example validation --release -- --scene core --output target/validation-core
git add "$APP_ROOT/app/crates/rendering" "$APP_ROOT/app/tests/visual" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "test: add deterministic ant visual validation"
```

---

### Task 12: Define platform contracts, fail-safe overlay states, and live wgpu surface creation

**Files:**
- Create: `$APP_ROOT/app/crates/platform-api/Cargo.toml`
- Create: `$APP_ROOT/app/crates/platform-api/src/{lib.rs,overlay.rs,event.rs,app_identity.rs}`
- Modify: `$APP_ROOT/app/crates/rendering/Cargo.toml`
- Modify: `$APP_ROOT/app/crates/rendering/src/renderer.rs`

**Interfaces:**

```rust
pub enum OverlaySafetyState { Created, Transparent, NonActivating, ClickThrough, SafeToShow, HiddenUnsafe }

pub struct AppIdentity { pub stable_id: String, pub display_name: String }
pub struct ForegroundContext { pub app: AppIdentity, pub fullscreen: bool }

pub enum UtilityAction { ToggleVisible, TogglePause, OpenSettings, SetPreset(String), ToggleLaunchAtLogin, Quit }
pub enum PlatformEvent {
    DisplaysChanged,
    Suspend,
    Resume,
    ForegroundChanged(Option<ForegroundContext>),
    PanicHotkey,
    UtilityAction(UtilityAction),
    QuitRequested,
}

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
    fn foreground_context(&self) -> Result<Option<ForegroundContext>, PlatformError>;
    fn recent_apps(&self) -> Result<Vec<AppIdentity>, PlatformError>;
    fn poll_events(&mut self) -> Result<Vec<PlatformEvent>, PlatformError>;
}
```

Rendering adds:

```rust
pub struct SurfaceRenderer { /* wgpu::Surface + config + display metadata */ }
impl Renderer {
    pub fn create_surface(&mut self, source: SurfaceSource, size: PixelSize, refresh_hz: f32) -> Result<SurfaceRenderer, RenderError>;
    pub fn render_surface(&mut self, surface: &mut SurfaceRenderer, instances: &[CreatureRenderInstance]) -> Result<RenderStats, RenderError>;
}
```

The native `OverlayWindow` owns the underlying OS window and must outlive its `SurfaceRenderer`. If `wgpu::SurfaceTargetUnsafe::RawHandle` is used, document that exact lifetime invariant in the unsafe block.

- [ ] **Step 1: Test overlay state-machine visibility rules.**

Mock windows in Created/Transparent/NonActivating/ClickThrough cannot show until safety verification moves them to SafeToShow. Failure moves to HiddenUnsafe.

- [ ] **Step 2: Test panic events independent of focus and tray/menu actions as events.**

No business logic belongs in native menu callbacks; they emit `UtilityAction`.

- [ ] **Step 3: Implement live wgpu surface creation after the platform type exists.**

Configure each surface using supported compositor present modes so render cadence can track 60/120/144/165/240 Hz displays independently of the 30 Hz biological tick.

- [ ] **Step 4: Verify and commit.**

```bash
cargo test -p platform-api -p rendering
cargo clippy -p platform-api -p rendering --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/platform-api" "$APP_ROOT/app/crates/rendering" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: define fail-safe overlay and live surface contracts"
```

---

### Task 13: Implement the macOS AppKit adapter

**Files:**
- Create: `$APP_ROOT/app/crates/platform-macos/Cargo.toml`
- Create: `$APP_ROOT/app/crates/platform-macos/src/{lib.rs,overlay.rs,status_item.rs,hotkey.rs,login.rs,display.rs}`
- Create/update: `$APP_ROOT/app/tests/input-safety/README.md`

**Interfaces:**
- Implements `PlatformAdapter` and `OverlayWindow`.

- [ ] **Step 1: Write macOS-only display/safety tests.**

Retina backing scale stays separate from physical calibration. Failed transparent/nonactivating/click-through setup never reaches SafeToShow.

- [ ] **Step 2: Implement display enumeration.**

Use AppKit/CoreGraphics for screen frame, backing scale, pixel dimensions/refresh where available, rotation, stable fingerprint data, and trustworthy physical metadata. Uncertain size is `NeedsManual`.

- [ ] **Step 3: Implement one native transparent borderless non-activating click-through overlay per display.**

Ignore mouse events, avoid ordinary Dock/Cmd-Tab presence, and choose a permissible high window level without fighting protected/system surfaces. Keep unsafe FFI blocks small and invariant-documented.

- [ ] **Step 4: Verify pass-through before show.**

Programmatic native state checks + manual input-safety matrix. Any failure keeps the overlay hidden.

- [ ] **Step 5: Implement NSStatusItem, global panic hotkey, launch-at-login, Spaces/full-screen metadata, and sleep/wake/display notifications.**

Menu callbacks emit `UtilityAction`; they do not own app state.

- [ ] **Step 6: Validate on actual macOS release build.**

On Apple Silicon:

```bash
cargo test --workspace --release --target aarch64-apple-darwin
cargo build -p platform-macos --release --target aarch64-apple-darwin
```

Manually test underlying click/double-click/drag/scroll/mouse motion/keyboard focus, Mission Control/Spaces, full-screen video, borderless game if available, monitor hot-plug, and panic hotkey. Record unsupported cases honestly.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/platform-macos" "$APP_ROOT/app/tests/input-safety" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add safe macOS click-through overlay adapter"
```

---

### Task 14: Implement the Windows Win32/DWM adapter

**Files:**
- Create: `$APP_ROOT/app/crates/platform-windows/Cargo.toml`
- Create: `$APP_ROOT/app/crates/platform-windows/src/{lib.rs,overlay.rs,tray.rs,hotkey.rs,startup.rs,display.rs}`
- Update: `$APP_ROOT/app/tests/input-safety/README.md`

**Interfaces:**
- Implements the same platform contracts.

- [ ] **Step 1: Write Windows-only DPI/display/safety tests.**

Cover negative virtual-desktop origins, mixed per-monitor DPI, and refusal to show before explicit pass-through state is established.

- [ ] **Step 2: Enable per-monitor-v2 DPI awareness before any overlay/settings window creation.**

Logical scaling must never be used as physical ant size.

- [ ] **Step 3: Implement transparent non-activating tool-style overlays with explicit hit-test pass-through.**

Use Win32/DWM styles and normal topmost ordering; do not constantly reassert over legitimate secure/system surfaces.

- [ ] **Step 4: Implement display/topology notifications.**

Handle display change, per-monitor DPI change, suspend/resume/session notifications, hot-plug, refresh/rotation where available, stable monitor identity, and trustworthy physical metadata.

- [ ] **Step 5: Implement `Shell_NotifyIconW`, `RegisterHotKey`, and per-user launch-at-login.**

No service, kernel driver, or admin requirement for normal operation. Tray callbacks emit `UtilityAction`.

- [ ] **Step 6: Validate on actual x64 Windows release build.**

```powershell
cargo test --workspace --release --target x86_64-pc-windows-msvc
cargo build -p platform-windows --release --target x86_64-pc-windows-msvc
```

Manually test input pass-through, Alt-Tab/taskbar absence, full-screen video, borderless game if available, mixed-DPI monitors, hot-plug, and panic hotkey.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/crates/platform-windows" "$APP_ROOT/app/tests/input-safety" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add safe Windows click-through overlay adapter"
```

---

### Task 15: Build the desktop app shell, diagnostics, settings UI, and calibration flow

**Files:**
- Create: `$APP_ROOT/app/crates/desktop-app/Cargo.toml`
- Create: `$APP_ROOT/app/crates/desktop-app/src/{main.rs,app.rs,lifecycle.rs,diagnostics.rs}`
- Create: `$APP_ROOT/app/crates/settings/src/{ui.rs,calibration_ui.rs}`

**Interfaces:**

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

pub struct DiagnosticsSnapshot {
    pub creature_count: usize,
    pub rendered_count: usize,
    pub simulation_ms: f32,
    pub behavior_ms: f32,
    pub spatial_ms: f32,
    pub trails_ms: f32,
    pub render_prep_ms: f32,
    pub gpu_ms: Option<f32>,
    pub upload_bytes: u64,
    pub lod_counts: [u32; 3],
    pub dropped_ticks: u64,
    pub seed: u64,
}
```

- [ ] **Step 1: Write app-state tests before the UI.**

Hide All immediately hides overlays and freezes simulation but is nonpersistent. Pause persists. Closing Settings does not stop creatures. Low-confidence first-run opens calibration; trusted calibration starts Realistic silently.

- [ ] **Step 2: Implement the composition root.**

Main loop: poll `PlatformEvent` -> map `UtilityAction`/hotkey into `AppCommand` -> apply config/lifecycle -> rebuild display topology if dirty -> advance fixed simulation -> convert `VisualCreatureState` into per-display render instances -> render only visible SafeToShow surfaces -> draw settings only when open -> collect diagnostics.

Use `#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]` in `main.rs` so Windows has no console. macOS packaging later uses `LSUIElement`.

- [ ] **Step 3: Implement compact egui settings.**

Primary controls: Enable, Preset, Population, displays, Launch at Login, Cursor Reaction, Continuous/Independent, Safe Overlay Mode, Panic Hotkey, Secondary Creatures, Advanced. Advanced: population cap/spawn pressure/trails/activity, creature scale, seed, developer diagnostics/visualizations. Secondary controls are generated dynamically from packaged qualified profiles; no fixed species names in UI code.

- [ ] **Step 4: Implement 85.60 mm credit-card calibration.**

Display a rectangle at the current candidate physical size; slider updates `mm_per_physical_px`; save per `DisplayFingerprint`. Creature scale remains a separate setting.

- [ ] **Step 5: Add hidden developer diagnostics.**

Expose creature ids/state colors, spatial grid, trail field, LOD visualization, physical coordinates, seed, forced population, and performance counters behind developer mode only.

- [ ] **Step 6: Verify and commit.**

```bash
cargo test -p settings -p desktop-app
cargo check --workspace --all-targets
cargo clippy --workspace --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/settings" "$APP_ROOT/app/crates/desktop-app" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: add utility shell settings and calibration UX"
```

---

### Task 16: Implement compatibility rules, continuous monitor migration, hidden-state efficiency, and renderer recovery

**Files:**
- Create: `$APP_ROOT/app/crates/desktop-app/src/compatibility.rs`
- Modify: `$APP_ROOT/app/crates/desktop-app/src/{app.rs,lifecycle.rs}`
- Modify: `$APP_ROOT/app/crates/simulation/src/state.rs`
- Modify: `$APP_ROOT/app/crates/rendering/src/renderer.rs`

**Interfaces:**

```rust
pub enum VisibilityDecision { Show, HideCompatibility, HidePanic, HidePaused, HideUnsafe }
pub struct CompatibilityPolicy { /* safe-mode + AppExclusion rules */ }
```

- [ ] **Step 1: Test continuous vs independent monitor behavior.**

Crossing from a 220 PPI display to 110 PPI preserves mm/s velocity/heading but changes pixel velocity. Independent mode treats the same edge as exterior.

- [ ] **Step 2: Test per-app and full-screen compatibility rules.**

Use `ForegroundContext.fullscreen` plus stable app id. Exclusions are metadata-only; no screenshot/OCR/content inspection path may appear.

- [ ] **Step 3: Test hidden-state efficiency.**

Panic-hidden all displays -> zero render submissions + frozen biology. One hidden monitor -> zero draws for that monitor while its state remains valid.

- [ ] **Step 4: Inject renderer/device loss.**

Required sequence: hide affected overlay -> keep utility controls alive -> bounded renderer/surface recreation -> rebuild GPU resources from CPU state -> re-run overlay safety verification -> show only after success. Permanent failure remains hidden and reports the diagnostic.

- [ ] **Step 5: Implement and verify.**

```bash
cargo test -p desktop-app -p simulation -p rendering
cargo clippy --workspace --all-targets -- -D warnings
git add "$APP_ROOT/app/crates/desktop-app" "$APP_ROOT/app/crates/simulation" "$APP_ROOT/app/crates/rendering" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: handle topology compatibility and renderer recovery"
```

---

### Task 17: Qualify and integrate up to two secondary creatures

**Files:**
- Create: `$APP_ROOT/docs/SECONDARY_CREATURE_GATE.md`
- Regenerate: `$APP_ROOT/app/assets/creature-profiles/runtime-profiles.bin` through profile compiler when needed
- Modify species-specific simulation/rendering modules only for passing candidates
- Modify: `$APP_ROOT/app/crates/settings/src/ui.rs`

- [ ] **Step 1: Evaluate each research candidate factually.**

Record evidence depth, calibrated morphology, locomotion/behavior measurements, legal data/asset status, implementation complexity, screen-size readability, and predicted performance cost.

- [ ] **Step 2: Apply the quality gate.**

A candidate passes only if it can have its own evidence-backed locomotion/render behavior rather than being an ant skin. Do not add unrelated full flight physics just to ship an extra creature. Zero passing candidates is a valid v1 result.

- [ ] **Step 3: Write species-specific failing tests before any passing candidate implementation.**

For example, a springtail candidate needs an evidence-derived jump/landing model rather than an ant walk state plus vertical animation.

- [ ] **Step 4: Integrate behind `CreatureKind` dispatch without weakening ant-specialized internals.**

Settings discovers qualified secondary profiles dynamically.

- [ ] **Step 5: Re-run ant regression suite/visuals and commit.**

```bash
cargo test --workspace
cargo run -p rendering --example validation --release -- --scene core --output target/validation-post-secondary
git add "$APP_ROOT/docs/SECONDARY_CREATURE_GATE.md" "$APP_ROOT/app" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "feat: gate and integrate evidence-qualified secondary creatures"
```

---

### Task 18: Instrument, benchmark, profile, and optimize 1,000+ creatures

**Files:**
- Create: `$APP_ROOT/app/crates/desktop-app/benches/{simulation.rs,stress.rs}`
- Modify: `$APP_ROOT/app/crates/desktop-app/Cargo.toml`
- Modify: `$APP_ROOT/app/crates/desktop-app/src/{main.rs,diagnostics.rs}`
- Modify measured hot paths only
- Create/update: `$APP_ROOT/docs/PERFORMANCE.md`
- Create/update: `$APP_ROOT/app/tests/soak/README.md`

- [ ] **Step 1: Add Criterion bench declarations and four fixed scenarios.**

```toml
[[bench]]
name = "simulation"
harness = false

[[bench]]
name = "stress"
harness = false
```

Scenarios: Realistic=50, Heavy=500, Required Stress=1,000, Extreme=2,000-5,000. Fixed seed + exact profile version.

- [ ] **Step 2: Add developer benchmark CLI.**

`desktop-app` accepts only in developer/CI invocation:

```text
--benchmark-scenario realistic --json <file>
--benchmark-scenario heavy --json <file>
--benchmark-scenario stress1000 --json <file>
--benchmark-scenario extreme --json <file>
```

Normal no-argument launch remains the silent utility.

- [ ] **Step 3: Measure the complete metric set.**

Simulation, behavior, spatial, trails, render prep, GPU time when supported, upload bytes, overlay overhead, total frame time, steady-state allocations, resident memory, LOD counts, dropped ticks. Report median/p95/p99/worst.

- [ ] **Step 4: Capture pre-optimization release baselines.**

```bash
cargo bench --workspace
cargo run -p desktop-app --release -- --benchmark-scenario realistic --json target/bench-realistic.json
cargo run -p desktop-app --release -- --benchmark-scenario heavy --json target/bench-heavy.json
cargo run -p desktop-app --release -- --benchmark-scenario stress1000 --json target/bench-1000.json
cargo run -p desktop-app --release -- --benchmark-scenario extreme --json target/bench-extreme.json
```

Inspect files; architecture alone is not performance evidence.

- [ ] **Step 5: Profile before changing hot paths.**

Use Instruments/platform profiler available on the current host. Optimize measured costs only: layout/cache, allocations, spatial bucket churn, trail scheduling, instance upload, shader overdraw, hidden surfaces, or decision cadence.

- [ ] **Step 6: Enforce hard invariants.**

No O(n^2) neighbor loop, no per-creature draw call, zero warmed steady-state simulation allocations, no capacity-growth spawn hitch after warm-up, zero hidden-overlay submissions, paused state near idle.

- [ ] **Step 7: Re-run until the 1,000-creature reference target passes or hardware access is the only blocker.**

Reference: <=16.67 ms frame budget at 60 FPS on available ordinary modern hardware with no recurring p99 stutter. If representative GPU access is unavailable, finish measurable CPU/simulation optimization and document the hardware validation blocker honestly.

- [ ] **Step 8: Soak the extreme scenario.**

Target >=2 hours when environment permits, exercising spawn/exit, hide/show, preset changes, config writes, renderer recreation, and topology events. Record start/end memory plus OS/GPU resource counts; no unbounded growth/trend.

- [ ] **Step 9: Write measured results and commit.**

```bash
git add "$APP_ROOT/app" "$APP_ROOT/docs/PERFORMANCE.md" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "perf: validate and optimize thousand-creature workloads"
```

---

### Task 19: Package macOS/Windows and add project CI/release workflows

**Files:**
- Create: `$APP_ROOT/app/packaging/macos/{Info.plist,build-dmg.sh}`
- Create: `$APP_ROOT/app/packaging/windows/wix/Product.wxs`
- Create: `$APP_ROOT/app/packaging/windows/build-msi.ps1`
- Create/modify: project-specific `.github/workflows/*.yml` on selected project lineage
- Create: `$APP_ROOT/docs/RELEASE.md`

- [ ] **Step 1: Add packaging smoke assertions before release scripts.**

macOS: executable, `Info.plist`, `LSUIElement`, profiles/presets/shaders, minimal entitlements. Windows: GUI subsystem/no console, assets, install/uninstall metadata, no service/driver requirement.

- [ ] **Step 2: Implement macOS `.app` + `.dmg`.**

Primary Apple Silicon. Add x86_64/Universal 2 only if toolchain/dependencies reasonably support it. Unsigned/ad-hoc development artifacts must build without signing credentials. If signing/notarization secrets exist, use them; otherwise record unsigned status and continue.

- [ ] **Step 3: Implement Windows x64 GUI binary + conventional per-user installer.**

No kernel component. Code signing is conditional on available credentials; unsigned test MSI remains valid.

- [ ] **Step 4: Add normal CI.**

At minimum:

```text
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
profile-compiler compile + verify
macOS release build
Windows release build
```

Renderer validation/benchmark jobs can be separate/manual/nightly where hosted GPU capability is insufficient.

- [ ] **Step 5: Add tag-triggered release workflow.**

Clean checkout -> compile/verify profiles -> tests -> release builds -> package -> optional sign/notarize -> SHA-256 checksums -> release artifacts. Print app version, profile version, and schema version separately.

- [ ] **Step 6: Exercise actual packaging as far as current target environments permit and inspect package contents.**

A YAML workflow is not evidence that a non-host package works; use target CI/hardware where available.

- [ ] **Step 7: Commit.**

```bash
git add "$APP_ROOT/app/packaging" "$APP_ROOT/docs/RELEASE.md" .github/workflows "$APP_ROOT/APP_EXECUTION.md"
git commit -m "build: package and validate macOS and Windows releases"
```

---

### Task 20: Finish docs, full verification, input-safety blocker, and handoff

**Files:**
- Create/update: `$APP_ROOT/README.md`
- Create/update: `$APP_ROOT/docs/{ARCHITECTURE.md,PLATFORM_SUPPORT.md,BIOLOGY_PROFILE_FORMAT.md,TESTING.md,PERFORMANCE.md,RELEASE.md}`
- Update: `$APP_ROOT/APP_EXECUTION.md`

- [ ] **Step 1: Write docs from actual implementation/results.**

`ARCHITECTURE.md`: crate boundaries/dependency direction. `PLATFORM_SUPPORT.md`: verified/best-effort/unsupported compositor/full-screen cases. `BIOLOGY_PROFILE_FORMAT.md`: evidence -> compiler -> runtime bundle. `TESTING.md`: automated/manual matrices. `PERFORMANCE.md`: hardware/backend + measured percentiles. `RELEASE.md`: reproducible packaging/signing/notarization.

- [ ] **Step 2: Run full project verification after the last code change.**

```bash
cd "$APP_ROOT/app"
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --release
cargo check --workspace --all-targets
cargo run -p profile-compiler --release -- compile --input "<resolved-mega-pack-root>" --output target/final-profiles.bin --report target/final-profiles.json
cargo run -p profile-compiler --release -- verify --bundle target/final-profiles.bin
cargo run -p rendering --example validation --release -- --scene all --output target/final-validation
cargo run -p desktop-app --release -- --benchmark-scenario stress1000 --json target/final-bench-1000.json
```

From repo root:

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

- [ ] **Step 3: Execute the platform input-safety release blocker.**

With overlay active, an underlying test app must receive click, double-click, drag, scroll, mouse movement, and keyboard input normally. Verify panic hotkey while another app/game has focus. Any failing target is not releasable; fix and rerun.

- [ ] **Step 4: Inspect final release-mode visuals at calibrated 1:1 physical size.**

2/3/4 mm, 360-degree heading sweep, gait/antenna coupling, bright/dark backgrounds, LOD transitions, high refresh where hardware exists. Record concrete observations about shimmer, missing appendages, shadows/outlines, and transition stability.

- [ ] **Step 5: Inspect produced packages, not just exit codes.**

List/open `.app`, `.dmg`, Windows binary, installer; verify profiles/presets/shaders/icons/metadata, launch behavior, signature/notarization state.

- [ ] **Step 6: Scan for unfinished markers and prohibited architecture creep.**

```bash
rg -n "TODO|TBD|FIXME|unimplemented!\(|todo!\(|panic!\(\"not implemented" "$APP_ROOT/app" "$APP_ROOT/docs"
rg -n "screen.?capture|OCR|inject|graphics.?hook|kernel driver|telemetry|analytics" "$APP_ROOT/app"
```

Review every match; remove unfinished required implementation or move intentional future/non-v1 notes into documentation.

- [ ] **Step 7: Map every design section 1-16 to implementation evidence in `APP_EXECUTION.md`.**

A gap reopens the owning earlier task. Implement/fix and rerun affected tests before proceeding.

- [ ] **Step 8: Commit final verified docs.**

```bash
git add "$APP_ROOT/README.md" "$APP_ROOT/docs" "$APP_ROOT/APP_EXECUTION.md"
git commit -m "docs: record verified insect desktop app release"
```

- [ ] **Step 9: Record coordination result and release all scopes.**

Use `agentctl.py event` with exact test/benchmark/artifact evidence. Mark completed only if all available-environment gates pass; otherwise mark blocked with the exact external blocker and completed unaffected work. Release every lease and set the one-shot worker offline/retired per repository policy.

- [ ] **Step 10: Final report.**

Report branch/final SHA, implemented features, test results/counts, measured 1,000-creature performance + hardware, macOS/Windows validation status, artifact paths, secondary-creature gate result, known platform limitations, signing/notarization status, and genuine blockers. Do not call an unverified target complete.

---

# Self-review coverage record

- Spec Sections 1-4: Global Constraints + Tasks 1-3.
- Section 5 ant simulation: Tasks 6-8.
- Section 6 rendering: Tasks 9-11.
- Section 7 platform/overlay: Tasks 12-16.
- Section 8 settings/UX: Tasks 4, 5, 15, 16.
- Sections 9-10 performance/testing: Tasks 6-11, 16, 18, 20.
- Sections 11-12 packaging/CI/versioning: Tasks 2, 3, 19.
- Section 13 documentation: Tasks 17-20.
- Section 14 one-shot Astra execution: One-shot contract + continuous Task 1-20 instruction.
- Section 15 definition of done: Task 20 + checklist below.
- Section 16 non-goals: Global Constraints + non-goals reminder.

Self-review also checked dependency direction and execution order: simulation exports `VisualCreatureState` rather than importing rendering; offscreen renderer exists before platform types; `SurfaceSource` is introduced before live surfaces; platform menus emit platform-neutral `UtilityAction` before the app shell maps it; benchmark crates exist before benchmark commands; all multi-name `cargo test` filters were replaced with valid commands.

# Definition of Done

The one-shot execution is finished only when, as far as the available environment permits, all are evidenced:

- macOS menu-bar utility launches without terminal; Windows tray utility launches without console.
- Transparent overlays render correctly and normal input passes through.
- Panic hide works independently of overlay focus.
- True physical size uses trusted metadata or per-display manual calibration.
- Profiles compile deterministically from Mega Pack evidence with provenance; missing biology is not invented.
- Ant simulation is deterministic in tests and bounded by measured profile distributions rather than primary random-waypoint/Perlin motion.
- Gait/antennae follow locomotion/behavior; stopped ants stop walking.
- Independent/continuous monitors work and preserve physical speed across density changes.
- Presets/config migration/corruption recovery work; Realistic keeps cursor reaction OFF.
- Maximum overlay mode and compatibility/per-app exclusions work without process injection or screen capture.
- Required 1,000-creature workload meets the defined 60 FPS reference target on available representative hardware, or unavailable hardware is the only documented validation blocker after measurable optimization is complete.
- High-refresh interpolation is smooth on available 120/144/165/240 Hz hardware.
- Validation shows no major LOD popping, persistent subpixel appendage disappearance/shimmer, giant game-style shadows, or forced glow outlines.
- Hidden overlays submit no render work; paused state approaches idle.
- Device-loss/failure paths cannot leave an opaque or input-trapping topmost window.
- Soak/stress testing shows no unbounded memory/handle/GPU-resource growth or increasing degradation.
- Secondary creatures are included only if the evidence/quality gate passes; ants remain uncompromised.
- Installable/testable macOS and Windows artifacts exist as far as target toolchains/hardware permit; missing signing credentials do not block unsigned artifacts.
- CI/release workflows/checksums and separate app/profile/schema versions exist.
- `README.md`, `ARCHITECTURE.md`, `PLATFORM_SUPPORT.md`, `BIOLOGY_PROFILE_FORMAT.md`, `PERFORMANCE.md`, `RELEASE.md`, and `TESTING.md` describe actual verified behavior.
- Final release-mode tests, benchmarks, visuals, packages, and platform outputs are inspected after the last source change before completion is claimed.

# Non-goals reminder

Do not spend the one-shot request adding accounts, cloud sync, analytics, mandatory auto-update, app-store integration, game/process injection, UI/content understanding, elaborate feeding/health gameplay, per-ant full rigid-body/3D physics, Linux release work, or weakly evidenced secondary species. Those are outside v1 unless the user explicitly changes the approved design.
