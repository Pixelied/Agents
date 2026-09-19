//! Real controller + instancing + completed offscreen GPU work. No native FPS claim.
use crate::{
    controller::Controller,
    metrics::{self, Distribution, ProcessSample},
};
use creature_profile::RuntimeProfileBundle;
use display_model::*;
use rendering::{InstanceBuilder, OffscreenTarget, Renderer};
use serde::Serialize;
use settings::{AppConfig, PresetId};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    path::Path,
    sync::Arc,
    time::{Duration, Instant},
};
type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;
pub const FIXED_SEED: u64 = 0xA17_2026;
pub const FRAME_DT: Duration = Duration::from_nanos(16_666_667);

#[derive(Clone, Copy, Debug, clap::ValueEnum, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Scenario {
    Realistic,
    Heavy,
    Stress1000,
    Extreme,
}
impl Scenario {
    pub fn config(self) -> Result<AppConfig> {
        let mut config = AppConfig::default();
        config.apply_preset(match self {
            Self::Realistic => PresetId::Realistic,
            Self::Heavy => PresetId::Heavy,
            Self::Stress1000 | Self::Extreme => PresetId::Nightmare,
        })?;
        if matches!(self, Self::Extreme) {
            config.set_population(5000)?;
        }
        config.developer_mode = true;
        config.advanced.seed = FIXED_SEED;
        Ok(config)
    }
}
pub fn reference_display(id: u64, width: u32, height: u32, ppi: f32) -> DisplaySurface {
    DisplaySurface {
        id: DisplayId(id),
        fingerprint: format!("benchmark-reference-{id}"),
        name: "Synthetic calibrated reference (not a physical display)".into(),
        pixels: PixelSize { width, height },
        desktop_bounds: DesktopRect {
            x: 0.,
            y: 0.,
            width: width as f64,
            height: height as f64,
        },
        scale_factor: 1.,
        refresh_hz: 60.,
        rotation_deg: 0,
        calibration: DisplayCalibration {
            mm_per_physical_px: 25.4 / ppi,
            confidence: CalibrationConfidence::Manual,
        },
    }
}
pub fn fixture(profiles: Arc<RuntimeProfileBundle>, scenario: Scenario) -> Result<Controller> {
    let mut controller = Controller::new(scenario.config()?, profiles)?;
    controller.set_displays(vec![reference_display(1, 1920, 1080, 110.)], false)?;
    controller.force_population()?; // Explicit deterministic benchmark placement, before warm-up.
    Ok(controller)
}
pub fn wait_for_gpu(gpu: &mut Renderer) -> Result<()> {
    gpu.device().poll(wgpu::PollType::Wait {
        submission_index: None,
        timeout: Some(Duration::from_secs(30)),
    })?;
    gpu.poll()?;
    Ok(())
}
#[derive(Clone, Debug, Serialize)]
pub struct FrameSample {
    pub frame: u32,
    pub ants: usize,
    pub ticks: u32,
    pub dropped_ticks: u64,
    pub simulation_ms: f64,
    pub behavior_ms: f64,
    pub spatial_ms: f64,
    pub trails_ms: f64,
    pub instance_prep_ms: f64,
    pub render_prep_ms: f64,
    pub gpu_ms: Option<f64>,
    pub total_ms: f64,
    pub draws: u32,
    pub upload_bytes: u64,
    pub lod_counts: [u32; 3],
    pub simulation_allocations: Option<u64>,
}
pub struct Harness {
    pub controller: Controller,
    pub gpu: Renderer,
    pub target: OffscreenTarget,
    pub builder: InstanceBuilder,
}
impl Harness {
    pub fn new(profiles: Arc<RuntimeProfileBundle>, scenario: Scenario) -> Result<Self> {
        let controller = fixture(profiles.clone(), scenario)?;
        let gpu = pollster::block_on(Renderer::new_headless(&profiles.creatures[0]))?;
        let target = gpu.offscreen(1920, 1080)?;
        let builder =
            InstanceBuilder::new(controller.simulation().capacity(), &profiles.creatures[0])?;
        Ok(Self {
            controller,
            gpu,
            target,
            builder,
        })
    }
    pub fn frame(&mut self, frame: u32, visible: bool, dt: Duration) -> Result<FrameSample> {
        let start = Instant::now();
        let simulation_start = Instant::now();
        let (result, allocations) =
            metrics::measure_allocations(|| self.controller.advance(dt, None, visible));
        let advance = result?;
        let simulation_ms = simulation_start.elapsed().as_secs_f64() * 1000.;
        let mut sample = FrameSample {
            frame,
            ants: self.controller.simulation().len(),
            ticks: advance.ticks,
            dropped_ticks: advance.dropped_ticks,
            simulation_ms,
            behavior_ms: advance.timings.behavior_ms,
            spatial_ms: advance.timings.spatial_ms,
            trails_ms: advance.timings.trails_ms,
            instance_prep_ms: 0.,
            render_prep_ms: 0.,
            gpu_ms: None,
            total_ms: 0.,
            draws: 0,
            upload_bytes: 0,
            lod_counts: [0; 3],
            simulation_allocations: allocations,
        };
        if visible && !self.controller.lifecycle.frozen(self.controller.config()) {
            let prep = Instant::now();
            let frame = self.builder.build(
                self.controller.simulation().visual_states(),
                &self.controller.displays()[0],
                self.controller.alpha,
                self.controller.config().creature_scale,
            )?;
            sample.instance_prep_ms = prep.elapsed().as_secs_f64() * 1000.;
            let render = self.gpu.render_offscreen(&self.target, frame)?;
            // A submit-only timer hides GPU cost. This benchmark deliberately serializes completion.
            wait_for_gpu(&mut self.gpu)?;
            sample.render_prep_ms = render.cpu_render_prep_ms;
            sample.gpu_ms = self.gpu.last_gpu_ms();
            sample.draws = render.submitted_draws;
            sample.upload_bytes = render.upload_bytes;
            sample.lod_counts = render.lod_counts;
        }
        sample.total_ms = start.elapsed().as_secs_f64() * 1000.;
        Ok(sample)
    }
    pub fn recreate_gpu(&mut self) -> Result<()> {
        let signature = self.controller.simulation().signature();
        self.gpu.device().destroy();
        // A real destruction callback must fail health, not silently continue on old resources.
        let loss_detected = self.gpu.poll().is_err() || self.gpu.check_health().is_err();
        if !loss_detected {
            return Err("destroyed device was not detected".into());
        }
        let profile = &self.controller.profiles().creatures[0];
        let gpu = pollster::block_on(Renderer::new_headless(profile))?;
        let target = gpu.offscreen(1920, 1080)?;
        self.target = target;
        self.gpu = gpu;
        if signature != self.controller.simulation().signature() {
            return Err("GPU recreation changed biology".into());
        }
        Ok(())
    }
}

#[derive(Serialize)]
pub struct BenchmarkReport {
    pub report_schema: u32,
    pub app_version: &'static str,
    pub profile_version: String,
    pub profile_schema: u32,
    pub bundled_profile_sha256: String,
    pub scenario: Scenario,
    pub seed: u64,
    pub mode: &'static str,
    pub host_os: &'static str,
    pub host_arch: &'static str,
    pub adapter: serde_json::Value,
    pub dimensions: [u32; 2],
    pub reference_ppi: f32,
    pub warmup_frames: u32,
    pub frames: u32,
    pub population: BTreeMap<&'static str, u64>,
    pub metrics_ms: BTreeMap<&'static str, Option<Distribution>>,
    pub process_before: ProcessSample,
    pub process_after: ProcessSample,
    pub gpu_before: Option<BTreeMap<&'static str, usize>>,
    pub gpu_after: Option<BTreeMap<&'static str, usize>>,
    pub last_image_nonzero_alpha_pixels: usize,
    pub native_input_verified: bool,
    pub native_60fps_qualified: bool,
    pub offscreen_frame_budget_16_67ms_p99_pass: bool,
    pub required_population_pass: bool,
    pub overlay_overhead_ms: Option<f64>,
    pub limitations: Vec<&'static str>,
    pub samples: Vec<FrameSample>,
}
pub fn run(
    profiles: Arc<RuntimeProfileBundle>,
    scenario: Scenario,
    frames: u32,
    warmup: u32,
    path: &Path,
) -> Result<()> {
    if !(1..=100_000).contains(&frames) || warmup > 10_000 {
        return Err("frame count must be 1..100000; warm-up 0..10000".into());
    }
    let mut h = Harness::new(profiles.clone(), scenario)?;
    for i in 0..warmup {
        h.frame(i, true, FRAME_DT)?;
    }
    let before = metrics::process_sample();
    let gpu_before = metrics::gpu_inventory(&h.gpu);
    let mut samples = Vec::with_capacity(frames as usize);
    for i in 0..frames {
        samples.push(h.frame(i, true, FRAME_DT)?);
    }
    let after = metrics::process_sample();
    let gpu_after = metrics::gpu_inventory(&h.gpu);
    let pixels = h.gpu.read_rgba(&h.target)?;
    let coverage = pixels
        .as_chunks::<4>()
        .0
        .iter()
        .filter(|p| p[3] != 0)
        .count();
    if coverage == 0 {
        return Err("rendered output has no insect coverage".into());
    }
    let mut metrics_ms = BTreeMap::new();
    for (name, values) in [
        (
            "total",
            samples.iter().map(|s| s.total_ms).collect::<Vec<_>>(),
        ),
        (
            "simulation",
            samples.iter().map(|s| s.simulation_ms).collect(),
        ),
        ("behavior", samples.iter().map(|s| s.behavior_ms).collect()),
        ("spatial", samples.iter().map(|s| s.spatial_ms).collect()),
        ("trails", samples.iter().map(|s| s.trails_ms).collect()),
        (
            "instance_prep",
            samples.iter().map(|s| s.instance_prep_ms).collect(),
        ),
        (
            "render_prep",
            samples.iter().map(|s| s.render_prep_ms).collect(),
        ),
        ("gpu", samples.iter().filter_map(|s| s.gpu_ms).collect()),
    ] {
        metrics_ms.insert(name, Distribution::new(&values)?);
    }
    let minimum = samples.iter().map(|s| s.ants).min().unwrap();
    let maximum = samples.iter().map(|s| s.ants).max().unwrap();
    let budget = metrics_ms["total"].as_ref().unwrap().p99 <= 16.67;
    let info = h.gpu.adapter_info();
    let report = BenchmarkReport {
        report_schema: 1,
        app_version: env!("CARGO_PKG_VERSION"),
        profile_version: profiles.profile_version.clone(),
        profile_schema: profiles.schema_version,
        bundled_profile_sha256: format!(
            "{:x}",
            Sha256::digest(include_bytes!(
                "../../../assets/creature-profiles/runtime-profiles.bin"
            ))
        ),
        scenario,
        seed: FIXED_SEED,
        mode: "offscreen_serialized",
        host_os: std::env::consts::OS,
        host_arch: std::env::consts::ARCH,
        adapter: serde_json::json!({"name":info.name,"vendor":info.vendor,"device":info.device,"device_type":format!("{:?}",info.device_type),"backend":format!("{:?}",info.backend),"driver":info.driver,"driver_info":info.driver_info}),
        dimensions: [1920, 1080],
        reference_ppi: 110.,
        warmup_frames: warmup,
        frames,
        population: BTreeMap::from([
            ("min", minimum as u64),
            ("max", maximum as u64),
            ("target", h.controller.config().target_population as u64),
        ]),
        metrics_ms,
        process_before: before,
        process_after: after,
        gpu_before,
        gpu_after,
        last_image_nonzero_alpha_pixels: coverage,
        native_input_verified: false,
        native_60fps_qualified: false,
        offscreen_frame_budget_16_67ms_p99_pass: budget,
        required_population_pass: !matches!(scenario, Scenario::Stress1000) || minimum >= 1000,
        overlay_overhead_ms: None,
        limitations: vec![
            "Completed offscreen work, not native compositor/presentation timing.",
            "The 110-PPI display is a calibrated numerical fixture, not a physical-size hardware test.",
            "Missing allocation counts require rebuilding with --features allocation-metrics.",
            "GPU registry counts are resource inventory, not VRAM bytes.",
            "GPU timestamps cover the ant pass; total time includes completion waiting and excludes report/readback serialization.",
        ],
        samples,
    };
    metrics::write_json(path, &report)
}
