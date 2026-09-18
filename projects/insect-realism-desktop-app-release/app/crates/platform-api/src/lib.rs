//! A small platform boundary: native callbacks emit events; application policy stays in the shell.
mod event;
mod hotkey;
mod overlay;
use display_model::{DisplayId, DisplaySurface};
pub use event::*;
pub use hotkey::*;
pub use overlay::*;

#[derive(Debug, thiserror::Error)]
pub enum PlatformError {
    #[error("native platform operation failed: {0}")]
    Native(String),
    #[error("overlay remains hidden: {0}")]
    Unsafe(&'static str),
    #[error("unsupported platform capability: {0}")]
    Unsupported(&'static str),
    #[error("invalid or unavailable panic shortcut: {0}")]
    Hotkey(String),
    #[error("native UI operation must run on the main thread")]
    WrongThread,
}
pub trait OverlayWindow {
    fn display_id(&self) -> DisplayId;
    fn safety_state(&self) -> OverlaySafetyState;
    fn verify_input_passthrough(&self) -> Result<(), PlatformError>;
    fn set_visible(&self, visible: bool) -> Result<(), PlatformError>;
    fn is_visible(&self) -> bool;
    fn mark_unsafe(&self);
    fn surface_source(&self) -> Result<SurfaceSource, PlatformError>;
}
pub trait PlatformAdapter {
    fn enumerate_displays(&self) -> Result<Vec<DisplaySurface>, PlatformError>;
    fn create_overlay(
        &mut self,
        display: &DisplaySurface,
    ) -> Result<Box<dyn OverlayWindow>, PlatformError>;
    /// Rebinding must keep the previous registered shortcut if the replacement fails.
    fn register_panic_hotkey(&mut self, binding: &str) -> Result<(), PlatformError>;
    fn set_launch_at_login(&self, enabled: bool) -> Result<(), PlatformError>;
    fn foreground_context(&self) -> Result<Option<ForegroundContext>, PlatformError>;
    fn recent_apps(&self) -> Result<Vec<AppIdentity>, PlatformError>;
    fn poll_events(&mut self) -> Result<Vec<PlatformEvent>, PlatformError>;
    fn cursor_position(&self) -> Result<Option<DesktopCursor>, PlatformError>;
    fn set_utility_state(&mut self, state: &UtilityState);
    /// The borrowed handle remains valid throughout the call; no native pointer is retained.
    fn window_display(
        &self,
        window: &dyn raw_window_handle::HasWindowHandle,
    ) -> Result<Option<DisplayId>, PlatformError>;
    fn move_settings_to_display(
        &self,
        window: &dyn raw_window_handle::HasWindowHandle,
        display: DisplayId,
    ) -> Result<(), PlatformError>;
    /// Only called in response to an explicit user action when the GPU settings UI is unavailable.
    fn present_diagnostic(&self, message: &str);
}
/// Stable deterministic identity, not a security hash or telemetry identifier.
pub fn display_id(fingerprint: &str) -> DisplayId {
    let mut hash = 0xcbf29ce484222325u64;
    for b in fingerprint.bytes() {
        hash = (hash ^ u64::from(b)).wrapping_mul(0x100000001b3);
    }
    DisplayId(hash)
}
/// Saved millimeters-per-backing-pixel values cannot be blindly reused at a new pixel mode.
pub fn calibration_key(display: &DisplaySurface) -> String {
    format!(
        "{}@{}x{}r{}",
        display.fingerprint, display.pixels.width, display.pixels.height, display.rotation_deg
    )
}

/// Calibration is only accepted when the entire interactive window lies on one monitor.
pub fn contains_desktop_rect(
    outer: display_model::DesktopRect,
    inner: display_model::DesktopRect,
) -> bool {
    [
        outer.x,
        outer.y,
        outer.width,
        outer.height,
        inner.x,
        inner.y,
        inner.width,
        inner.height,
    ]
    .iter()
    .all(|v| v.is_finite())
        && inner.width > 0.0
        && inner.height > 0.0
        && outer.width > 0.0
        && outer.height > 0.0
        && inner.x >= outer.x
        && inner.y >= outer.y
        && inner.right() <= outer.right()
        && inner.bottom() <= outer.bottom()
}
