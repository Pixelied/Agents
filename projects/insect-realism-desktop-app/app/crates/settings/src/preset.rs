use crate::ConfigError;
use serde::{Deserialize, Serialize};
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum PresetId {
    Realistic,
    Light,
    Heavy,
    Nightmare,
    Custom,
}
impl PresetId {
    pub fn label(self) -> &'static str {
        match self {
            Self::Realistic => "Realistic",
            Self::Light => "Light Infestation",
            Self::Heavy => "Heavy Infestation",
            Self::Nightmare => "Nightmare",
            Self::Custom => "Custom",
        }
    }
}
#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Preset {
    pub id: PresetId,
    pub population: u32,
    pub spawn_per_second: f32,
    pub trail_strength: f32,
    pub trail_lifetime_multiplier: f32,
    pub cursor_reaction: bool,
    pub activity: f32,
}
impl Preset {
    pub fn validate(&self) -> Result<(), ConfigError> {
        if self.id == PresetId::Custom
            || self.population > 10_000
            || self.activity != 1.0
            || !self.spawn_per_second.is_finite()
            || !(0.1..=2000.0).contains(&self.spawn_per_second)
            || !self.trail_strength.is_finite()
            || !(0.0..=2.0).contains(&self.trail_strength)
            || !self.trail_lifetime_multiplier.is_finite()
            || !(0.1..=10.0).contains(&self.trail_lifetime_multiplier)
        {
            return Err(ConfigError::Invalid("preset values"));
        }
        Ok(())
    }
}
pub fn presets() -> Result<Vec<Preset>, ConfigError> {
    [
        include_str!("../../../assets/presets/realistic.toml"),
        include_str!("../../../assets/presets/light.toml"),
        include_str!("../../../assets/presets/heavy.toml"),
        include_str!("../../../assets/presets/nightmare.toml"),
    ]
    .into_iter()
    .map(|s| {
        let p: Preset = toml::from_str(s).map_err(|e| ConfigError::Format(e.to_string()))?;
        p.validate()?;
        Ok(p)
    })
    .collect()
}
pub(crate) fn preset(id: PresetId) -> Result<Preset, ConfigError> {
    presets()?
        .into_iter()
        .find(|p| p.id == id)
        .ok_or(ConfigError::Invalid("custom has no preset definition"))
}
