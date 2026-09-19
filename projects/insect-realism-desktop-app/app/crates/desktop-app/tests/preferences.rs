use desktop_app::preferences::*;
use platform_api::PlatformError;
use settings::*;
use std::cell::RefCell;
#[derive(Default)]
struct Effects {
    binding: String,
    login: RefCell<bool>,
    fail_login: bool,
}
impl PreferenceEffects for Effects {
    fn bind(&mut self, b: &str) -> Result<(), PlatformError> {
        if b.contains("F12") {
            return Err(PlatformError::Hotkey("occupied".into()));
        }
        self.binding = b.into();
        Ok(())
    }
    fn login(&self, b: bool) -> Result<(), PlatformError> {
        if self.fail_login && b {
            return Err(PlatformError::Native("denied".into()));
        }
        *self.login.borrow_mut() = b;
        Ok(())
    }
}
struct FailStore;
impl ConfigStore for FailStore {
    fn load(&self) -> Result<AppConfig, ConfigError> {
        Ok(AppConfig::default())
    }
    fn save_atomic(&self, _: &AppConfig) -> Result<(), ConfigError> {
        Err(ConfigError::Format("disk full".into()))
    }
}
#[test]
fn failed_disk_write_rolls_back_os_settings_and_shortcut() {
    let old = AppConfig::default();
    let mut new = old.clone();
    new.panic_hotkey = "Ctrl+Shift+J".into();
    new.launch_at_login = true;
    let mut effects = Effects {
        binding: old.panic_hotkey.clone(),
        ..Effects::default()
    };
    assert!(apply_preferences(&old, &new, &mut effects, Some(&FailStore)).is_err());
    assert_eq!(effects.binding, old.panic_hotkey);
    assert!(!*effects.login.borrow());
}
#[test]
fn conflicting_shortcut_and_login_failure_leave_previous_controls() {
    let old = AppConfig::default();
    let mut new = old.clone();
    new.panic_hotkey = "Ctrl+F12".into();
    let mut effects = Effects {
        binding: old.panic_hotkey.clone(),
        ..Effects::default()
    };
    assert!(apply_preferences(&old, &new, &mut effects, None).is_err());
    assert_eq!(effects.binding, old.panic_hotkey);
    new.panic_hotkey = "Ctrl+J".into();
    new.launch_at_login = true;
    effects.fail_login = true;
    assert!(apply_preferences(&old, &new, &mut effects, None).is_err());
    assert_eq!(effects.binding, old.panic_hotkey);
}
#[test]
fn read_only_session_applies_changes_without_touching_future_file() {
    let old = AppConfig::default();
    let mut new = old.clone();
    new.panic_hotkey = "Ctrl+J".into();
    new.launch_at_login = true;
    let mut effects = Effects::default();
    apply_preferences(&old, &new, &mut effects, None).unwrap();
    assert_eq!(effects.binding, new.panic_hotkey);
    assert!(*effects.login.borrow());
}
