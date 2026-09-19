use crate::lifecycle::Lifecycle;
use creature_profile::RuntimeProfileBundle;
use display_model::*;
use platform_api::{HotkeyBinding, calibration_key};
use settings::AppConfig;
use simulation::*;
use std::{sync::Arc, time::Duration};
#[derive(Debug, thiserror::Error)]
#[error("{0}")]
pub struct AppError(pub String);
fn err(e: impl std::fmt::Display) -> AppError {
    AppError(e.to_string())
}
/// The composition's platform-independent state. The native shell never edits biology.
pub struct Controller {
    config: AppConfig,
    pub lifecycle: Lifecycle,
    native_displays: Vec<DisplaySurface>,
    displays: Vec<DisplaySurface>,
    topology: DisplayTopology,
    simulation: Simulation,
    profiles: Arc<RuntimeProfileBundle>,
    pub alpha: f32,
    pub last_simulation: SimulationTimings,
    pub dropped_ticks: u64,
}
fn model(
    native: &[DisplaySurface],
    config: &AppConfig,
) -> Result<(Vec<DisplaySurface>, DisplayTopology), AppError> {
    let mut displays = native.to_vec();
    for d in &mut displays {
        d.validate().map_err(err)?;
        if let Some(mm) = config.display_calibration.get(&calibration_key(d)) {
            d.calibration = DisplayCalibration::resolve(Some(*mm), None).map_err(err)?;
        }
    }
    let enabled = displays
        .iter()
        .filter(|d| d.calibration.is_ready() && config.display_is_enabled(&d.fingerprint))
        .cloned()
        .collect();
    let topology = DisplayTopology::new(enabled, config.continuous_monitors).map_err(err)?;
    Ok((displays, topology))
}
fn configure(
    simulation: &mut Simulation,
    config: &AppConfig,
    topology: &DisplayTopology,
) -> Result<(), AppError> {
    simulation
        .configure_population(
            config.advanced.spawn_per_second,
            config.advanced.activity,
            config.advanced.trail_strength,
            config.advanced.trail_lifetime_multiplier,
            config.cursor_reaction,
        )
        .map_err(err)?;
    let target = config
        .target_population
        .saturating_mul(topology.displays().len() as u32)
        .min(config.advanced.population_cap);
    simulation.set_target_population(target).map_err(err)
}
impl Controller {
    pub fn new(config: AppConfig, profiles: Arc<RuntimeProfileBundle>) -> Result<Self, AppError> {
        config.validate().map_err(err)?;
        HotkeyBinding::parse(&config.panic_hotkey).map_err(err)?;
        profiles.validate().map_err(err)?;
        let mut simulation = Simulation::new(
            SimulationConfig {
                tick_hz: 30,
                capacity: config.advanced.population_cap as usize,
                seed: config.advanced.seed,
            },
            profiles.clone(),
        )
        .map_err(err)?;
        let topology = DisplayTopology::new(vec![], config.continuous_monitors).map_err(err)?;
        configure(&mut simulation, &config, &topology)?;
        Ok(Self {
            config,
            lifecycle: Lifecycle::default(),
            native_displays: Vec::new(),
            displays: Vec::new(),
            topology,
            simulation,
            profiles,
            alpha: 0.,
            last_simulation: SimulationTimings::default(),
            dropped_ticks: 0,
        })
    }
    pub fn config(&self) -> &AppConfig {
        &self.config
    }
    pub fn displays(&self) -> &[DisplaySurface] {
        &self.displays
    }
    pub fn topology(&self) -> &DisplayTopology {
        &self.topology
    }
    pub fn simulation(&self) -> &Simulation {
        &self.simulation
    }
    pub fn profiles(&self) -> &Arc<RuntimeProfileBundle> {
        &self.profiles
    }
    pub fn set_displays(
        &mut self,
        native: Vec<DisplaySurface>,
        first_run: bool,
    ) -> Result<(), AppError> {
        let (displays, topology) = model(&native, &self.config)?;
        self.simulation
            .reconfigure_topology(&topology)
            .map_err(err)?;
        configure(&mut self.simulation, &self.config, &topology)?;
        if first_run
            && self.config.enabled
            && !self.config.paused
            && displays.iter().any(|d| {
                !d.calibration.is_ready() && self.config.display_is_enabled(&d.fingerprint)
            })
        {
            self.lifecycle.open_settings();
        }
        self.native_displays = native;
        self.displays = displays;
        self.topology = topology;
        Ok(())
    }
    /// Returns whether native display resources must be rebuilt. Ordinary preset changes preserve residents.
    pub fn apply_config(&mut self, config: AppConfig) -> Result<bool, AppError> {
        config.validate().map_err(err)?;
        HotkeyBinding::parse(&config.panic_hotkey).map_err(err)?;
        let (displays, topology) = model(&self.native_displays, &config)?;
        let geometry_changed = displays != self.displays
            || config.continuous_monitors != self.config.continuous_monitors
            || config.display_enabled != self.config.display_enabled;
        if config.advanced.seed != self.config.advanced.seed
            || config.advanced.population_cap != self.config.advanced.population_cap
        {
            let mut replacement = Simulation::new(
                SimulationConfig {
                    tick_hz: 30,
                    capacity: config.advanced.population_cap as usize,
                    seed: config.advanced.seed,
                },
                self.profiles.clone(),
            )
            .map_err(err)?;
            replacement.reconfigure_topology(&topology).map_err(err)?;
            configure(&mut replacement, &config, &topology)?;
            self.simulation = replacement;
        } else {
            if geometry_changed {
                self.simulation
                    .reconfigure_topology(&topology)
                    .map_err(err)?;
            }
            configure(&mut self.simulation, &config, &topology)?;
        }
        self.config = config;
        self.displays = displays;
        self.topology = topology;
        Ok(geometry_changed)
    }
    /// Explicit diagnostic placement only; never used during ordinary launch or preset changes.
    pub fn force_population(&mut self) -> Result<(), AppError> {
        if !self.config.developer_mode {
            return Err(AppError("Enable developer mode first".into()));
        }
        let mut batch = Vec::new();
        let remaining = self
            .simulation
            .capacity()
            .saturating_sub(self.simulation.len());
        for display in self.topology.displays() {
            let count = self
                .simulation
                .visual_states()
                .iter()
                .filter(|c| c.display == display.id)
                .count();
            let needed = (self.config.target_population as usize).saturating_sub(count);
            let size = display.size_mm();
            let columns = ((self.config.target_population as f32 * size.x / size.y)
                .sqrt()
                .ceil() as usize)
                .max(1);
            let rows = (self.config.target_population as usize)
                .div_ceil(columns)
                .max(1);
            for i in count..count + needed {
                if batch.len() >= remaining {
                    break;
                }
                let x = (i % columns) as f32 + 0.5;
                let y = (i / columns) as f32 + 0.5;
                batch.push((
                    display.id,
                    Vec2Mm::new(x / columns as f32 * size.x, y / rows as f32 * size.y)
                        .map_err(err)?,
                    i as f32 * 2.3999631,
                ));
            }
        }
        self.simulation.spawn_developer_batch(&batch).map_err(err)
    }
    pub fn advance(
        &mut self,
        dt: Duration,
        cursor: Option<CursorDisturbance>,
        any_visible: bool,
    ) -> Result<AdvanceReport, AppError> {
        self.simulation
            .set_paused(self.lifecycle.frozen(&self.config) || !any_visible);
        let cursor = if self.config.cursor_reaction {
            cursor
        } else {
            None
        };
        let report = self
            .simulation
            .advance(
                dt,
                &EnvironmentSnapshot {
                    topology: &self.topology,
                    cursor,
                },
            )
            .map_err(err)?;
        self.alpha = report.alpha;
        self.dropped_ticks = self.dropped_ticks.saturating_add(report.dropped_ticks);
        if report.ticks > 0 {
            self.last_simulation = report.timings;
        }
        Ok(report)
    }
}
