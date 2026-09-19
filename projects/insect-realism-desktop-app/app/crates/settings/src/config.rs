use crate::{PresetId, migrate, preset::preset};
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fs,
    io::Write,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};
use thiserror::Error;
pub const CONFIG_VERSION: u32 = 1;
const MAX_CONFIG_BYTES: u64 = 1_048_576;
#[derive(Debug, Error)]
pub enum ConfigError {
    #[error("configuration IO: {0}")]
    Io(#[from] std::io::Error),
    #[error("configuration format: {0}")]
    Format(String),
    #[error("configuration version {0} is newer than this app; the file was left unchanged")]
    FutureVersion(u64),
    #[error("invalid configuration field: {0}")]
    Invalid(&'static str),
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum AppExclusionMode {
    Hide,
    Compatibility,
}
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct AppExclusion {
    pub stable_id: String,
    pub mode: AppExclusionMode,
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct AdvancedConfig {
    pub population_cap: u32,
    pub spawn_per_second: f32,
    pub trail_strength: f32,
    pub trail_lifetime_multiplier: f32,
    /// Changes low-motion propensity, never a preset speed multiplier.
    pub activity: f32,
    pub seed: u64,
    pub show_ids: bool,
    pub show_states: bool,
    pub show_grid: bool,
    pub show_trails: bool,
    pub show_lod: bool,
}
impl Default for AdvancedConfig {
    fn default() -> Self {
        Self {
            population_cap: 5000,
            spawn_per_second: 3.0,
            trail_strength: 0.65,
            trail_lifetime_multiplier: 1.0,
            activity: 1.0,
            seed: 0xA17_2026,
            show_ids: false,
            show_states: false,
            show_grid: false,
            show_trails: false,
            show_lod: false,
        }
    }
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct AppConfig {
    pub schema_version: u32,
    pub enabled: bool,
    pub paused: bool,
    pub preset: PresetId,
    /// Population per enabled, calibrated monitor.
    pub target_population: u32,
    pub cursor_reaction: bool,
    pub continuous_monitors: bool,
    pub launch_at_login: bool,
    pub safe_overlay_mode: bool,
    pub panic_hotkey: String,
    pub creature_scale: f32,
    pub developer_mode: bool,
    pub display_calibration: BTreeMap<String, f32>,
    pub display_enabled: BTreeMap<String, bool>,
    pub app_exclusions: Vec<AppExclusion>,
    pub secondary_creatures: BTreeMap<String, bool>,
    pub advanced: AdvancedConfig,
}
impl Default for AppConfig {
    fn default() -> Self {
        let p =
            preset(PresetId::Realistic).expect("the bundled Realistic preset is covered by tests");
        Self {
            schema_version: CONFIG_VERSION,
            enabled: true,
            paused: false,
            preset: PresetId::Realistic,
            target_population: p.population,
            cursor_reaction: p.cursor_reaction,
            continuous_monitors: true,
            launch_at_login: false,
            safe_overlay_mode: false,
            panic_hotkey: "Ctrl+Alt+Shift+H".into(),
            creature_scale: 1.0,
            developer_mode: false,
            display_calibration: BTreeMap::new(),
            display_enabled: BTreeMap::new(),
            app_exclusions: Vec::new(),
            secondary_creatures: BTreeMap::new(),
            advanced: AdvancedConfig {
                spawn_per_second: p.spawn_per_second,
                trail_strength: p.trail_strength,
                trail_lifetime_multiplier: p.trail_lifetime_multiplier,
                activity: p.activity,
                ..AdvancedConfig::default()
            },
        }
    }
}
impl AppConfig {
    pub fn validate(&self) -> Result<(), ConfigError> {
        if self.schema_version > CONFIG_VERSION {
            return Err(ConfigError::FutureVersion(self.schema_version.into()));
        }
        if self.schema_version != CONFIG_VERSION {
            return Err(ConfigError::Invalid("schema_version"));
        }
        if !(1..=10_000).contains(&self.advanced.population_cap)
            || self.target_population > self.advanced.population_cap
        {
            return Err(ConfigError::Invalid("population"));
        }
        if !self.creature_scale.is_finite() || !(0.25..=8.0).contains(&self.creature_scale) {
            return Err(ConfigError::Invalid("creature_scale"));
        }
        for (value, range, name) in [
            (self.advanced.spawn_per_second, 0.1..=2000.0, "spawn rate"),
            (self.advanced.trail_strength, 0.0..=2.0, "trail strength"),
            (
                self.advanced.trail_lifetime_multiplier,
                0.1..=10.0,
                "trail lifetime",
            ),
            (self.advanced.activity, 0.25..=2.0, "activity"),
        ] {
            if !value.is_finite() || !range.contains(&value) {
                return Err(ConfigError::Invalid(name));
            }
        }
        if self.panic_hotkey.trim().is_empty() || self.panic_hotkey.len() > 80 {
            return Err(ConfigError::Invalid("panic_hotkey"));
        }
        if self.display_calibration.len() > 64
            || self.display_enabled.len() > 64
            || self.app_exclusions.len() > 256
        {
            return Err(ConfigError::Invalid("configuration capacity"));
        }
        for (id, mm) in &self.display_calibration {
            if id.is_empty() || id.len() > 512 || !mm.is_finite() || !(0.02..=1.0).contains(mm) {
                return Err(ConfigError::Invalid("display calibration"));
            }
        }
        if self
            .app_exclusions
            .iter()
            .any(|e| e.stable_id.is_empty() || e.stable_id.len() > 4096)
        {
            return Err(ConfigError::Invalid("application exclusion"));
        }
        Ok(())
    }
    pub fn set_population(&mut self, population: u32) -> Result<(), ConfigError> {
        if population > self.advanced.population_cap {
            return Err(ConfigError::Invalid("population"));
        }
        if self.target_population != population {
            self.target_population = population;
            self.preset = PresetId::Custom;
        }
        Ok(())
    }
    pub fn apply_preset(&mut self, id: PresetId) -> Result<(), ConfigError> {
        let p = preset(id)?;
        self.advanced.population_cap = self.advanced.population_cap.max(p.population);
        self.target_population = p.population;
        self.cursor_reaction = p.cursor_reaction;
        self.advanced.spawn_per_second = p.spawn_per_second;
        self.advanced.trail_strength = p.trail_strength;
        self.advanced.trail_lifetime_multiplier = p.trail_lifetime_multiplier;
        self.advanced.activity = p.activity;
        self.creature_scale = 1.0;
        self.preset = id;
        self.validate()
    }
    pub fn display_is_enabled(&self, fingerprint: &str) -> bool {
        self.display_enabled
            .get(fingerprint)
            .copied()
            .unwrap_or(true)
    }
}
pub trait ConfigStore {
    fn load(&self) -> Result<AppConfig, ConfigError>;
    fn save_atomic(&self, config: &AppConfig) -> Result<(), ConfigError>;
}
pub struct LoadReport {
    pub config: AppConfig,
    pub corrupt_backup: Option<PathBuf>,
}
pub struct JsonConfigStore {
    path: PathBuf,
}
impl JsonConfigStore {
    pub fn new(path: PathBuf) -> Self {
        Self { path }
    }
    pub fn path(&self) -> &Path {
        &self.path
    }
    pub fn load_report(&self) -> Result<LoadReport, ConfigError> {
        let metadata = match fs::metadata(&self.path) {
            Ok(m) => m,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                return Ok(LoadReport {
                    config: AppConfig::default(),
                    corrupt_backup: None,
                });
            }
            Err(e) => return Err(e.into()),
        };
        let decoded = if metadata.len() > MAX_CONFIG_BYTES {
            Err(ConfigError::Invalid("file too large"))
        } else {
            migrate::decode(&fs::read(&self.path)?)
        };
        match decoded {
            Ok(config) => Ok(LoadReport {
                config,
                corrupt_backup: None,
            }),
            Err(e @ ConfigError::FutureVersion(_)) => Err(e),
            Err(_) => {
                let stamp = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_nanos();
                let name = self.path.file_name().unwrap_or_default().to_string_lossy();
                let backup = self
                    .path
                    .with_file_name(format!("{name}.corrupt-{stamp}-{}", std::process::id()));
                if backup.exists() {
                    return Err(ConfigError::Invalid("corrupt backup collision"));
                }
                fs::rename(&self.path, &backup)?;
                Ok(LoadReport {
                    config: AppConfig::default(),
                    corrupt_backup: Some(backup),
                })
            }
        }
    }
}
impl ConfigStore for JsonConfigStore {
    fn load(&self) -> Result<AppConfig, ConfigError> {
        Ok(self.load_report()?.config)
    }
    fn save_atomic(&self, config: &AppConfig) -> Result<(), ConfigError> {
        config.validate()?;
        if let Ok(meta) = fs::metadata(&self.path)
            && meta.len() <= MAX_CONFIG_BYTES
            && let Ok(value) = serde_json::from_slice::<serde_json::Value>(&fs::read(&self.path)?)
            && let Some(v) = value.get("schema_version").and_then(|v| v.as_u64())
            && v > u64::from(CONFIG_VERSION)
        {
            return Err(ConfigError::FutureVersion(v));
        }
        let parent = self
            .path
            .parent()
            .filter(|p| !p.as_os_str().is_empty())
            .unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        let mut temporary = tempfile::NamedTempFile::new_in(parent)?;
        let bytes =
            serde_json::to_vec_pretty(config).map_err(|e| ConfigError::Format(e.to_string()))?;
        temporary.write_all(&bytes)?;
        temporary.write_all(b"\n")?;
        temporary.as_file().sync_all()?;
        temporary.persist(&self.path).map_err(|e| e.error)?;
        #[cfg(unix)]
        if let Ok(directory) = fs::File::open(parent) {
            let _ = directory.sync_all();
        }
        Ok(())
    }
}
