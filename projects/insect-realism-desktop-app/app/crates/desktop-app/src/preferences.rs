use crate::controller::AppError;
use platform_api::{PlatformAdapter, PlatformError};
use settings::{AppConfig, ConfigStore};
pub trait PreferenceEffects {
    fn bind(&mut self, binding: &str) -> Result<(), PlatformError>;
    fn login(&self, enabled: bool) -> Result<(), PlatformError>;
}
impl<T: PlatformAdapter + ?Sized> PreferenceEffects for T {
    fn bind(&mut self, binding: &str) -> Result<(), PlatformError> {
        self.register_panic_hotkey(binding)
    }
    fn login(&self, enabled: bool) -> Result<(), PlatformError> {
        self.set_launch_at_login(enabled)
    }
}
/// Commit OS preferences and configuration together. Rollback failures are reported, never hidden.
pub fn apply_preferences(
    old: &AppConfig,
    new: &AppConfig,
    effects: &mut (impl PreferenceEffects + ?Sized),
    store: Option<&dyn ConfigStore>,
) -> Result<(), AppError> {
    new.validate().map_err(|e| AppError(e.to_string()))?;
    platform_api::HotkeyBinding::parse(&new.panic_hotkey).map_err(|e| AppError(e.to_string()))?;
    let binding_changed = old.panic_hotkey != new.panic_hotkey;
    let login_changed = old.launch_at_login != new.launch_at_login;
    if binding_changed {
        effects
            .bind(&new.panic_hotkey)
            .map_err(|e| AppError(e.to_string()))?;
    }
    let applied = (|| -> Result<(), AppError> {
        if login_changed {
            effects
                .login(new.launch_at_login)
                .map_err(|e| AppError(e.to_string()))?;
        }
        if let Some(store) = store {
            store
                .save_atomic(new)
                .map_err(|e| AppError(e.to_string()))?;
        }
        Ok(())
    })();
    if let Err(error) = applied {
        let mut message = error.to_string();
        if binding_changed && let Err(e) = effects.bind(&old.panic_hotkey) {
            message.push_str(&format!("; shortcut rollback failed: {e}"));
        }
        if login_changed && let Err(e) = effects.login(old.launch_at_login) {
            message.push_str(&format!("; login rollback failed: {e}"));
        }
        return Err(AppError(message));
    }
    Ok(())
}
