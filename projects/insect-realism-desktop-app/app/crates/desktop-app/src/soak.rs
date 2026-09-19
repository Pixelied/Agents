//! Bounded, wall-clock lifecycle stress. A headless soak is not native OS acceptance.
use crate::{
    benchmark::{self, FRAME_DT, Harness, Scenario},
    metrics::{self, Distribution},
};
use creature_profile::RuntimeProfileBundle;
use rendering::{InstanceBuilder, OffscreenTarget};
use serde::Serialize;
use settings::{ConfigStore, JsonConfigStore, PresetId};
use std::{
    collections::BTreeMap,
    path::Path,
    sync::Arc,
    time::{Duration, Instant},
};
type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

#[derive(Serialize)]
struct ResourceSample {
    wall_seconds: f64,
    frame: u64,
    ants: usize,
    phase: u64,
    process: metrics::ProcessSample,
    gpu: Option<BTreeMap<&'static str, usize>>,
}
#[derive(Serialize)]
struct Report {
    report_schema: u32,
    app_version: &'static str,
    profile_version: String,
    seed: u64,
    mode: &'static str,
    adapter: String,
    requested_seconds: u64,
    wall_seconds: f64,
    simulated_seconds: f64,
    frames: u64,
    initial_ants: usize,
    final_ants: usize,
    minimum_ants: usize,
    maximum_ants: usize,
    events: BTreeMap<&'static str, u64>,
    hidden_draws: u64,
    hidden_state_changes: u64,
    serialized_frame_ms: Option<Distribution>,
    process_before: metrics::ProcessSample,
    process_after: metrics::ProcessSample,
    resources: Vec<ResourceSample>,
    complete: bool,
    native_input_verified: bool,
    two_hour_wall_soak_completed: bool,
    limitations: Vec<&'static str>,
}
fn event(events: &mut BTreeMap<&'static str, u64>, name: &'static str) {
    *events.entry(name).or_default() += 1;
}
fn invariant(h: &Harness) -> Result<()> {
    if h.controller.simulation().len() > h.controller.simulation().capacity() {
        return Err("population exceeds storage".into());
    }
    for s in h.controller.simulation().visual_states() {
        let scalars = [
            s.position_mm.x,
            s.position_mm.y,
            s.previous_position_mm.x,
            s.previous_position_mm.y,
            s.heading_rad,
            s.body_length_mm,
            s.body_width_mm,
            s.gait_phase,
            s.speed_mm_s,
            s.antenna[0],
            s.antenna[1],
        ];
        if scalars.iter().any(|n| !n.is_finite()) || s.body_length_mm <= 0. || s.body_width_mm <= 0.
        {
            return Err(format!("nonfinite or invalid creature {}", s.id.0).into());
        }
        if !h
            .controller
            .topology()
            .displays()
            .iter()
            .any(|d| d.id == s.display)
        {
            return Err(format!("creature {} refers to absent display", s.id.0).into());
        }
    }
    Ok(())
}
pub fn run(
    profiles: Arc<RuntimeProfileBundle>,
    seconds: u64,
    cycle_frames: u32,
    path: &Path,
) -> Result<()> {
    if !(1..=86_400).contains(&seconds) || !(1..=60_000).contains(&cycle_frames) {
        return Err("soak seconds must be 1..86400 and cycle frames 1..60000".into());
    }
    let mut h = Harness::new(profiles.clone(), Scenario::Extreme)?;
    for i in 0..12 {
        h.frame(i, true, FRAME_DT)?;
    }
    let initial = h.controller.simulation().len();
    let mut report = Report {
        report_schema: 1,
        app_version: env!("CARGO_PKG_VERSION"),
        profile_version: profiles.profile_version.clone(),
        seed: benchmark::FIXED_SEED,
        mode: "wall-clock offscreen lifecycle stress",
        adapter: format!("{:?}", h.gpu.adapter_info()),
        requested_seconds: seconds,
        wall_seconds: 0.,
        simulated_seconds: 0.,
        frames: 0,
        initial_ants: initial,
        final_ants: initial,
        minimum_ants: initial,
        maximum_ants: initial,
        events: BTreeMap::new(),
        hidden_draws: 0,
        hidden_state_changes: 0,
        serialized_frame_ms: None,
        process_before: metrics::process_sample(),
        process_after: metrics::process_sample(),
        resources: Vec::new(),
        complete: false,
        native_input_verified: false,
        two_hour_wall_soak_completed: false,
        limitations: vec![
            "This run advances a deterministic 60-Hz time input; measured wall time is separate.",
            "No native windows, physical displays, OS hot-plug, input dispatch or installer are exercised.",
            "Temporary configuration files are isolated from user preferences.",
            "Live wgpu resource counts describe the current instance, not aggregate VRAM usage.",
            "A finite-duration run is evidence over that interval, not a proof that no unbounded leak exists.",
            "Frame-time samples are a bounded recent window; resource samples are taken every five wall-clock seconds.",
        ],
    };
    let temporary = tempfile::tempdir()?;
    let store = JsonConfigStore::new(temporary.path().join("soak-settings.json"));
    let mut second: Option<(OffscreenTarget, InstanceBuilder)> = None;
    let start = Instant::now();
    let duration = Duration::from_secs(seconds);
    let mut last_phase = None;
    let mut frozen_signature = None;
    let mut frozen_tick = 0;
    let mut sample_at = Duration::ZERO;
    let mut save_at = Duration::from_secs(15);
    let mut recent_times = Vec::with_capacity(4096);
    metrics::write_json(path, &report)?; // A recoverable, explicitly incomplete report exists during the run.
    while start.elapsed() < duration {
        let phase = (report.frames / u64::from(cycle_frames)) % 8;
        if last_phase != Some(phase) {
            if let Some(expected) = frozen_signature.take()
                && h.controller.simulation().signature() != expected
            {
                return Err("hidden or paused state changed".into());
            }
            h.controller.lifecycle.show_all();
            let mut config = h.controller.config().clone();
            config.paused = false;
            match phase {
                0 => {
                    config = Scenario::Extreme.config()?;
                    event(&mut report.events, "extreme_presets");
                }
                1 => {
                    h.controller.lifecycle.hide_all();
                    event(&mut report.events, "panic_hides");
                }
                2 => {
                    config.apply_preset(PresetId::Heavy)?;
                    event(&mut report.events, "heavy_presets");
                }
                3 => {
                    let first = benchmark::reference_display(1, 1920, 1080, 110.);
                    let mut other = benchmark::reference_display(2, 1280, 1024, 220.);
                    other.desktop_bounds.x = 1920.;
                    h.controller.set_displays(vec![first, other], false)?;
                    second = Some((
                        h.gpu.offscreen(1280, 1024)?,
                        InstanceBuilder::new(5000, &profiles.creatures[0])?,
                    ));
                    event(&mut report.events, "topology_changes");
                }
                4 => {
                    config.continuous_monitors = false;
                    event(&mut report.events, "independent_modes");
                }
                5 => {
                    second = None;
                    h.controller.set_displays(
                        vec![benchmark::reference_display(1, 1920, 1080, 110.)],
                        false,
                    )?;
                    event(&mut report.events, "topology_changes");
                }
                6 => {
                    second = None;
                    h.recreate_gpu()?;
                    event(&mut report.events, "gpu_recreations");
                }
                7 => {
                    config.paused = true;
                    event(&mut report.events, "persistent_pauses");
                }
                _ => unreachable!(),
            }
            h.controller.apply_config(config)?;
            store.save_atomic(h.controller.config())?;
            if store.load()? != *h.controller.config() {
                return Err("atomic configuration roundtrip mismatch".into());
            }
            event(&mut report.events, "config_roundtrips");
            if h.controller.lifecycle.frozen(h.controller.config()) {
                frozen_signature = Some(h.controller.simulation().signature());
                frozen_tick = h.controller.simulation().tick_count();
            }
            last_phase = Some(phase);
        }
        let visible = !h.controller.lifecycle.frozen(h.controller.config());
        let sample = h.frame(
            (report.frames % u64::from(u32::MAX)) as u32,
            visible,
            FRAME_DT,
        )?;
        if !visible {
            report.hidden_draws += u64::from(sample.draws);
            if h.controller.simulation().tick_count() != frozen_tick {
                report.hidden_state_changes += 1;
            }
            if report.hidden_draws != 0 || report.hidden_state_changes != 0 {
                return Err("hidden-state safety invariant failed".into());
            }
        } else if let Some((target, builder)) = &mut second {
            let instances = builder.build(
                h.controller.simulation().visual_states(),
                &h.controller.displays()[1],
                h.controller.alpha,
                1.,
            )?;
            h.gpu.render_offscreen(target, instances)?;
            benchmark::wait_for_gpu(&mut h.gpu)?;
            event(&mut report.events, "second_display_draws");
        }
        invariant(&h)?;
        if recent_times.len() == 4096 {
            recent_times.clear();
        }
        recent_times.push(sample.total_ms);
        report.frames += 1;
        report.final_ants = sample.ants;
        report.minimum_ants = report.minimum_ants.min(sample.ants);
        report.maximum_ants = report.maximum_ants.max(sample.ants);
        report.simulated_seconds += f64::from(sample.ticks) / 30.;
        let elapsed = start.elapsed();
        if elapsed >= sample_at {
            let process = metrics::process_sample();
            // Deliberately generous engineering guardrails, not a substitute for trend review.
            if process
                .resident_bytes
                .is_some_and(|n| n > 2 * 1024 * 1024 * 1024)
            {
                return Err("soak safety limit: process RSS exceeds 2 GiB".into());
            }
            report.resources.push(ResourceSample {
                wall_seconds: elapsed.as_secs_f64(),
                frame: report.frames,
                ants: sample.ants,
                phase,
                process,
                gpu: metrics::gpu_inventory(&h.gpu),
            });
            sample_at = elapsed + Duration::from_secs(5);
        }
        if elapsed >= save_at {
            report.wall_seconds = elapsed.as_secs_f64();
            report.process_after = metrics::process_sample();
            metrics::write_json(path, &report)?;
            save_at = elapsed + Duration::from_secs(15);
        }
    }
    if let Some(expected) = frozen_signature
        && h.controller.simulation().signature() != expected
    {
        return Err("final paused state changed".into());
    }
    benchmark::wait_for_gpu(&mut h.gpu)?;
    report.wall_seconds = start.elapsed().as_secs_f64();
    report.process_after = metrics::process_sample();
    report.serialized_frame_ms = Distribution::new(&recent_times)?;
    report.complete = true;
    report.two_hour_wall_soak_completed = report.wall_seconds >= 7200.;
    metrics::write_json(path, &report)
}
