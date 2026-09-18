use display_model::{DesktopRect, DisplayCalibration, DisplaySurface, PixelSize};
use platform_api::{HotkeyKey, PlatformError, display_id};
/// Per-monitor-v2 virtual desktop coordinates are backing pixels. DPI is UI scaling, not PPI.
pub fn monitor_surface(
    fingerprint: String,
    name: String,
    bounds: DesktopRect,
    scale: f64,
    refresh: f32,
    rotation: u16,
) -> Result<DisplaySurface, PlatformError> {
    if !bounds.width.is_finite()
        || !bounds.height.is_finite()
        || bounds.width <= 0.
        || bounds.height <= 0.
        || bounds.width > 32768.
        || bounds.height > 32768.
    {
        return Err(PlatformError::Native("invalid monitor pixel extent".into()));
    }
    let d = DisplaySurface {
        id: display_id(&fingerprint),
        fingerprint,
        name,
        pixels: PixelSize {
            width: bounds.width as u32,
            height: bounds.height as u32,
        },
        desktop_bounds: bounds,
        scale_factor: scale,
        refresh_hz: refresh,
        rotation_deg: rotation,
        calibration: DisplayCalibration::resolve(None, None)
            .map_err(|e| PlatformError::Native(e.to_string()))?,
    };
    d.validate()
        .map_err(|e| PlatformError::Native(e.to_string()))?;
    Ok(d)
}
pub fn virtual_key(key: HotkeyKey) -> Option<u32> {
    match key {
        HotkeyKey::Letter(c) if c.is_ascii_uppercase() => Some(u32::from(c)),
        HotkeyKey::Digit(n) if n <= 9 => Some(0x30 + u32::from(n)),
        HotkeyKey::Function(n) if (1..=12).contains(&n) => Some(0x70 + u32::from(n) - 1),
        HotkeyKey::Escape => Some(0x1b),
        HotkeyKey::Space => Some(0x20),
        _ => None,
    }
}
pub fn autostart_command(path: &str) -> Result<String, PlatformError> {
    if path.is_empty() || path.contains(['"', '\0', '\n', '\r']) {
        return Err(PlatformError::Native(
            "invalid executable path for startup registration".into(),
        ));
    }
    Ok(format!("\"{path}\""))
}

/// WM_CLOSE must not let DefWindowProc destroy a still-owned presentation HWND.
pub fn defer_native_close(message: u32) -> bool {
    message == 0x0010
}
