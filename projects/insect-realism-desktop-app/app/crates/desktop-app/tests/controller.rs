use creature_profile::RuntimeProfileBundle;
use desktop_app::controller::*;
use display_model::*;
use platform_api::calibration_key;
use settings::{AppConfig, ConfigStore, JsonConfigStore, PresetId};
use std::{sync::Arc, time::Duration};
fn profiles() -> Arc<RuntimeProfileBundle> {
    Arc::new(
        RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    )
}
fn display(id: u64, ready: bool) -> DisplaySurface {
    DisplaySurface {
        id: DisplayId(id),
        fingerprint: format!("panel-{id}"),
        name: "Fixture".into(),
        pixels: PixelSize {
            width: 1920,
            height: 1080,
        },
        desktop_bounds: DesktopRect {
            x: 1920. * (id - 1) as f64,
            y: 0.,
            width: 1920.,
            height: 1080.,
        },
        scale_factor: 2.,
        refresh_hz: 120.,
        rotation_deg: 0,
        calibration: DisplayCalibration::resolve(None, ready.then_some(0.25)).unwrap(),
    }
}
#[test]
fn uncertain_first_run_opens_calibration_and_never_enters_the_simulation() {
    let mut c = Controller::new(AppConfig::default(), profiles()).unwrap();
    c.set_displays(vec![display(1, false)], true).unwrap();
    assert!(c.lifecycle.settings_open);
    assert!(c.topology().displays().is_empty());
    assert_eq!(c.displays().len(), 1);
}
#[test]
fn trusted_first_run_starts_silently() {
    let mut c = Controller::new(AppConfig::default(), profiles()).unwrap();
    c.set_displays(vec![display(1, true)], true).unwrap();
    assert!(!c.lifecycle.settings_open);
    assert_eq!(c.topology().displays().len(), 1);
}
#[test]
fn saved_calibration_is_mode_specific_and_independent_of_ui_scale() {
    let d = display(1, false);
    let mut config = AppConfig::default();
    config.display_calibration.insert(calibration_key(&d), 0.1);
    let mut c = Controller::new(config, profiles()).unwrap();
    c.set_displays(vec![d.clone()], true).unwrap();
    assert!(!c.lifecycle.settings_open);
    assert_eq!(c.displays()[0].calibration.mm_per_physical_px, 0.1);
    let mut changed = d;
    changed.pixels.width = 2560;
    c.set_displays(vec![changed], false).unwrap();
    assert!(!c.displays()[0].calibration.is_ready());
    assert!(c.topology().displays().is_empty());
}
#[test]
fn panic_hide_freezes_real_simulation_and_close_settings_does_not() {
    let mut c = Controller::new(AppConfig::default(), profiles()).unwrap();
    c.set_displays(vec![display(1, true)], true).unwrap();
    for _ in 0..100 {
        c.advance(Duration::from_millis(40), None, true).unwrap();
    }
    assert!(!c.simulation().is_empty());
    let before = c.simulation().signature();
    c.lifecycle.hide_all();
    c.advance(Duration::from_secs(3600), None, true).unwrap();
    assert_eq!(before, c.simulation().signature());
    c.lifecycle.show_all();
    c.lifecycle.open_settings();
    c.lifecycle.close_settings();
    c.advance(Duration::from_millis(40), None, true).unwrap();
    assert_ne!(before, c.simulation().signature());
}
#[test]
fn independent_mode_and_disabled_monitors_rebuild_the_actual_topology() {
    let mut c = Controller::new(AppConfig::default(), profiles()).unwrap();
    c.set_displays(vec![display(1, true), display(2, true)], false)
        .unwrap();
    assert!(c.topology().connection_count() > 0);
    let mut config = c.config().clone();
    config.continuous_monitors = false;
    c.apply_config(config).unwrap();
    assert_eq!(c.topology().connection_count(), 0);
    let mut config = c.config().clone();
    config.display_enabled.insert("panel-2".into(), false);
    c.apply_config(config).unwrap();
    assert_eq!(c.topology().displays().len(), 1);
}
#[test]
fn persistent_pause_roundtrips_while_panic_hide_is_not_saved() {
    let mut c = Controller::new(AppConfig::default(), profiles()).unwrap();
    let mut config = c.config().clone();
    config.paused = true;
    config.apply_preset(PresetId::Light).unwrap();
    c.apply_config(config).unwrap();
    c.lifecycle.hide_all();
    let dir = tempfile::tempdir().unwrap();
    let store = JsonConfigStore::new(dir.path().join("prefs.json"));
    store.save_atomic(c.config()).unwrap();
    let restored = Controller::new(store.load().unwrap(), profiles()).unwrap();
    assert!(restored.config().paused);
    assert!(!restored.lifecycle.panic_hidden);
}
#[test]
fn paused_first_launch_does_not_open_calibration() {
    let config = AppConfig {
        paused: true,
        ..AppConfig::default()
    };
    let mut c = Controller::new(config, profiles()).unwrap();
    c.set_displays(vec![display(1, false)], true).unwrap();
    assert!(!c.lifecycle.settings_open);
}
#[test]
fn forced_population_requires_explicit_developer_mode_and_fills_without_repeated_rebuilds() {
    let mut c = Controller::new(AppConfig::default(), profiles()).unwrap();
    c.set_displays(vec![display(1, true)], true).unwrap();
    assert!(c.force_population().is_err());
    let mut cfg = c.config().clone();
    cfg.developer_mode = true;
    cfg.target_population = 1000;
    c.apply_config(cfg).unwrap();
    c.force_population().unwrap();
    assert_eq!(c.simulation().len(), 1000);
    c.force_population().unwrap();
    assert_eq!(c.simulation().len(), 1000);
}
