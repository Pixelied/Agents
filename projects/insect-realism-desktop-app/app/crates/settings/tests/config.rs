use settings::*;
use std::fs;

#[test]
fn realistic_is_small_offline_and_cursor_off() {
    let c = AppConfig::default();
    assert_eq!(c.preset, PresetId::Realistic);
    assert_eq!(c.target_population, 50);
    assert!(!c.cursor_reaction && !c.launch_at_login);
    c.validate().unwrap();
}
#[test]
fn presets_are_valid_data_not_speed_overrides() {
    let presets = presets().unwrap();
    assert_eq!(presets.len(), 4);
    for p in &presets {
        p.validate().unwrap();
    }
    let mut c = AppConfig::default();
    c.apply_preset(PresetId::Nightmare).unwrap();
    assert!(c.target_population >= 1000);
    assert_eq!(c.creature_scale, 1.0);
    assert_eq!(c.advanced.activity, 1.0);
    assert!(!c.cursor_reaction);
}
#[test]
fn editing_population_selects_custom() {
    let mut c = AppConfig::default();
    c.set_population(83).unwrap();
    assert_eq!(c.preset, PresetId::Custom);
    assert_eq!(c.target_population, 83);
}
#[test]
fn atomic_persistence_round_trips_and_replaces() {
    let dir = tempfile::tempdir().unwrap();
    let store = JsonConfigStore::new(dir.path().join("config.json"));
    let mut c = AppConfig::default();
    store.save_atomic(&c).unwrap();
    c.set_population(99).unwrap();
    c.display_calibration.insert("monitor-A".into(), 0.123);
    store.save_atomic(&c).unwrap();
    assert_eq!(store.load().unwrap(), c);
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
}
#[test]
fn invalid_save_does_not_destroy_last_good_config() {
    let dir = tempfile::tempdir().unwrap();
    let store = JsonConfigStore::new(dir.path().join("config.json"));
    let good = AppConfig::default();
    store.save_atomic(&good).unwrap();
    let before = fs::read(store.path()).unwrap();
    let mut bad = good;
    bad.creature_scale = f32::NAN;
    assert!(store.save_atomic(&bad).is_err());
    assert_eq!(before, fs::read(store.path()).unwrap());
}
#[test]
fn corrupt_bytes_are_preserved_before_recovery() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("config.json");
    fs::write(&path, b"{broken original").unwrap();
    let report = JsonConfigStore::new(path.clone()).load_report().unwrap();
    assert_eq!(report.config, AppConfig::default());
    let backup = report.corrupt_backup.unwrap();
    assert_eq!(fs::read(backup).unwrap(), b"{broken original");
    assert!(!path.exists());
}
#[test]
fn future_schema_is_not_silently_recovered_or_overwritten() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("config.json");
    let bytes = b"{\"schema_version\":999,\"future\":true}";
    fs::write(&path, bytes).unwrap();
    let error = JsonConfigStore::new(path.clone()).load().unwrap_err();
    assert!(matches!(error, ConfigError::FutureVersion(999)));
    assert_eq!(fs::read(path).unwrap(), bytes);
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
}
#[test]
fn version_zero_migrates_explicit_population_field() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("config.json");
    fs::write(
        &path,
        r#"{"schema_version":0,"population":91,"enabled":false}"#,
    )
    .unwrap();
    let c = JsonConfigStore::new(path).load().unwrap();
    assert_eq!(c.schema_version, CONFIG_VERSION);
    assert_eq!(c.target_population, 91);
    assert_eq!(c.preset, PresetId::Custom);
    assert!(!c.enabled);
}
#[test]
fn calibration_and_resource_limits_reject_invalid_values() {
    let mut c = AppConfig::default();
    c.display_calibration.insert("display".into(), 0.0);
    assert!(c.validate().is_err());
    c.display_calibration.clear();
    c.target_population = c.advanced.population_cap + 1;
    assert!(c.validate().is_err());
}
