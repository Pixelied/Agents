use crate::{AppConfig, CONFIG_VERSION, ConfigError, PresetId};
pub(crate) fn decode(bytes: &[u8]) -> Result<AppConfig, ConfigError> {
    let mut value: serde_json::Value =
        serde_json::from_slice(bytes).map_err(|e| ConfigError::Format(e.to_string()))?;
    let version = value
        .get("schema_version")
        .and_then(|v| v.as_u64())
        .ok_or(ConfigError::Invalid("schema_version"))?;
    if version > u64::from(CONFIG_VERSION) {
        return Err(ConfigError::FutureVersion(version));
    }
    if version == 0 {
        let object = value
            .as_object_mut()
            .ok_or(ConfigError::Invalid("object"))?;
        if let Some(pop) = object.remove("population") {
            object.insert("target_population".into(), pop);
            object.insert(
                "preset".into(),
                serde_json::to_value(PresetId::Custom)
                    .map_err(|e| ConfigError::Format(e.to_string()))?,
            );
        }
        object.insert("schema_version".into(), CONFIG_VERSION.into());
    }
    let config: AppConfig =
        serde_json::from_value(value).map_err(|e| ConfigError::Format(e.to_string()))?;
    config.validate()?;
    Ok(config)
}
