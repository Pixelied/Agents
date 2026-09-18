use super::*;
use display_model::DesktopRect;
pub(super) fn surface(monitor: HMONITOR) -> Result<DisplaySurface, PlatformError> {
    // SAFETY: All Win32 output structures have their documented size and live for each synchronous call.
    unsafe {
        let mut info = MONITORINFOEXW::default();
        info.monitorInfo.cbSize = std::mem::size_of::<MONITORINFOEXW>() as u32;
        if !GetMonitorInfoW(monitor, (&mut info as *mut MONITORINFOEXW).cast()).as_bool() {
            return Err(error(windows::core::Error::from_thread()));
        }
        let mut device = DISPLAY_DEVICEW {
            cb: std::mem::size_of::<DISPLAY_DEVICEW>() as u32,
            ..Default::default()
        };
        let have_device = EnumDisplayDevicesW(
            PCWSTR(info.szDevice.as_ptr()),
            0,
            &mut device,
            EDD_GET_DEVICE_INTERFACE_NAME,
        )
        .as_bool();
        let id = if have_device && !text(&device.DeviceID).is_empty() {
            format!("win:{}", text(&device.DeviceID).to_lowercase())
        } else {
            format!("win-fallback:{}", text(&info.szDevice))
        };
        let name = if have_device {
            text(&device.DeviceString)
        } else {
            text(&info.szDevice)
        };
        let mut mode = DEVMODEW {
            dmSize: std::mem::size_of::<DEVMODEW>() as u16,
            ..Default::default()
        };
        let have_mode = EnumDisplaySettingsW(
            PCWSTR(info.szDevice.as_ptr()),
            ENUM_CURRENT_SETTINGS,
            &mut mode,
        )
        .as_bool();
        let hz = if have_mode && mode.dmDisplayFrequency >= 20 {
            mode.dmDisplayFrequency as f32
        } else {
            60.
        };
        let rotation = if have_mode {
            (mode.Anonymous1.Anonymous2.dmDisplayOrientation.0 * 90) as u16
        } else {
            0
        };
        let (mut dx, mut dy) = (96, 96);
        let _ = GetDpiForMonitor(monitor, MDT_EFFECTIVE_DPI, &mut dx, &mut dy);
        let r = info.monitorInfo.rcMonitor;
        crate::monitor_surface(
            id,
            name,
            DesktopRect {
                x: f64::from(r.left),
                y: f64::from(r.top),
                width: f64::from(r.right - r.left),
                height: f64::from(r.bottom - r.top),
            },
            f64::from(dx.max(48)) / 96.,
            hz,
            rotation,
        )
    }
}
unsafe extern "system" fn callback(
    monitor: HMONITOR,
    _hdc: HDC,
    _rect: *mut RECT,
    data: LPARAM,
) -> windows::core::BOOL {
    // SAFETY: EnumDisplayMonitors is synchronous. `data` borrows the collecting Vec for this call only.
    let out = unsafe { &mut *(data.0 as *mut Vec<Result<DisplaySurface, PlatformError>>) };
    out.push(surface(monitor));
    true.into()
}
pub(super) fn enumerate() -> Result<Vec<DisplaySurface>, PlatformError> {
    let mut out: Vec<Result<DisplaySurface, PlatformError>> = Vec::new();
    if !unsafe {
        EnumDisplayMonitors(
            None,
            None,
            Some(callback),
            LPARAM((&mut out as *mut Vec<_>) as isize),
        )
    }
    .as_bool()
    {
        return Err(error(windows::core::Error::from_thread()));
    }
    out.into_iter().collect()
}
