use super::*;
use display_model::{DesktopRect, DisplayCalibration, PixelSize};
use objc2::rc::Retained;
use objc2_app_kit::NSScreen;
use objc2_core_foundation::CFRetained;
use objc2_core_graphics::*;
use objc2_foundation::{NSArray, NSDictionary, NSNumber, NSString, ns_string};
use std::ffi::{CStr, c_char, c_void};

#[link(name = "CoreGraphics", kind = "framework")]
unsafe extern "C" {
    fn CGDisplayCreateUUIDFromDisplayID(display: u32) -> *const c_void;
}
#[link(name = "CoreFoundation", kind = "framework")]
unsafe extern "C" {
    fn CFUUIDCreateString(allocator: *const c_void, uuid: *const c_void) -> *const c_void;
    fn CFStringGetCString(s: *const c_void, buf: *mut c_char, size: isize, encoding: u32) -> bool;
    fn CFRelease(value: *const c_void);
}
pub(super) fn fingerprint(id: u32) -> String {
    // SAFETY: Create/Copy ownership is balanced by CFRelease, buffers are bounded, handles never escape.
    unsafe {
        let uuid = CGDisplayCreateUUIDFromDisplayID(id);
        if !uuid.is_null() {
            let string = CFUUIDCreateString(std::ptr::null(), uuid);
            CFRelease(uuid);
            if !string.is_null() {
                let mut buf = [0 as c_char; 128];
                let ok = CFStringGetCString(string, buf.as_mut_ptr(), 128, 0x08000100);
                CFRelease(string);
                if ok {
                    return format!("mac:{}", CStr::from_ptr(buf.as_ptr()).to_string_lossy());
                }
            }
        }
    }
    // Fallback identity is deliberately called out in diagnostics; manual calibration keys include the pixel mode.
    format!(
        "mac-fallback:{}:{}:{}:{}",
        CGDisplayVendorNumber(id),
        CGDisplayModelNumber(id),
        CGDisplaySerialNumber(id),
        id
    )
}
pub(super) fn screen_id(screen: &NSScreen) -> u32 {
    screen
        .deviceDescription()
        .objectForKey(ns_string!("NSScreenNumber"))
        .and_then(|v| v.downcast::<NSNumber>().ok())
        .map(|v| v.unsignedIntValue())
        .unwrap_or(0)
}
pub(super) fn primary_top(mtm: MainThreadMarker) -> f64 {
    NSScreen::screens(mtm)
        .firstObject()
        .map(|s| {
            let r = s.frame();
            r.origin.y + r.size.height
        })
        .unwrap_or(0.)
}
pub(super) fn screen_for(mtm: MainThreadMarker, id: DisplayId) -> Option<Retained<NSScreen>> {
    NSScreen::screens(mtm)
        .iter()
        .find(|s| display_id(&fingerprint(screen_id(s))) == id)
}
pub(super) fn enumerate(mtm: MainThreadMarker) -> Result<Vec<DisplaySurface>, PlatformError> {
    let top = primary_top(mtm);
    let mut out = Vec::new();
    for screen in NSScreen::screens(mtm).iter() {
        let id = screen_id(&screen);
        if id == 0 {
            continue;
        }
        let frame = screen.frame();
        let scale = screen.backingScaleFactor();
        let pixels = PixelSize {
            width: (frame.size.width * scale).round() as u32,
            height: (frame.size.height * scale).round() as u32,
        };
        let rotation = CGDisplayRotation(id).round().rem_euclid(360.) as u16;
        let mm = CGDisplayScreenSize(id);
        let (mmw, mmh) = if rotation == 90 || rotation == 270 {
            (mm.height, mm.width)
        } else {
            (mm.width, mm.height)
        };
        let aspect_pixels = f64::from(pixels.width) / f64::from(pixels.height.max(1));
        let metadata = if CGDisplayIsBuiltin(id)
            && mmw > 100.
            && mmw < 1500.
            && mmh > 80.
            && mmh < 1000.
            && ((mmw / mmh) / aspect_pixels - 1.).abs() < 0.04
        {
            Some((mmw / f64::from(pixels.width)) as f32)
        } else {
            None
        };
        let mode = CGDisplayCopyDisplayMode(id);
        let hz = CGDisplayMode::refresh_rate(mode.as_deref());
        let hz = if hz.is_finite() && hz >= 20. {
            hz as f32
        } else {
            screen.maximumFramesPerSecond().max(60) as f32
        };
        let fp = fingerprint(id);
        let d = DisplaySurface {
            id: display_id(&fp),
            fingerprint: fp,
            name: screen.localizedName().to_string(),
            pixels,
            desktop_bounds: crate::desktop_rect(
                DesktopRect {
                    x: frame.origin.x,
                    y: frame.origin.y,
                    width: frame.size.width,
                    height: frame.size.height,
                },
                top,
            ),
            scale_factor: scale,
            refresh_hz: hz,
            rotation_deg: rotation,
            calibration: DisplayCalibration::resolve(None, metadata).map_err(native_error)?,
        };
        d.validate().map_err(native_error)?;
        out.push(d);
    }
    Ok(out)
}
fn number(dict: &NSDictionary, key: &NSString) -> Option<f64> {
    dict.objectForKey(key)?
        .downcast::<NSNumber>()
        .ok()
        .map(|x| x.doubleValue())
}
/// Public window metadata only. No pixel/image API, titles, accessibility probing, or recording permission.
pub(super) fn foreground_geometry(
    pid: i32,
    displays: &[DisplaySurface],
) -> (bool, Option<DisplayId>) {
    let Some(list) = CGWindowListCopyWindowInfo(
        CGWindowListOption::OptionOnScreenOnly | CGWindowListOption::ExcludeDesktopElements,
        0,
    ) else {
        return (false, None);
    };
    // SAFETY: The documented CGWindowListCopyWindowInfo return is a toll-free-bridged NSArray of NSDictionary.
    // The CF owner stays alive for the complete borrowed traversal, and only documented numeric fields are read.
    let list = unsafe {
        &*CFRetained::as_ptr(&list)
            .as_ptr()
            .cast::<NSArray<NSDictionary>>()
    };
    for info in list.iter() {
        if number(&info, ns_string!("kCGWindowOwnerPID")) != Some(f64::from(pid))
            || number(&info, ns_string!("kCGWindowLayer")) != Some(0.)
        {
            continue;
        }
        let Some(bounds) = info.objectForKey(ns_string!("kCGWindowBounds")) else {
            continue;
        };
        let Ok(bounds) = bounds.downcast::<NSDictionary>() else {
            continue;
        };
        let (Some(x), Some(y), Some(w), Some(h)) = (
            number(&bounds, ns_string!("X")),
            number(&bounds, ns_string!("Y")),
            number(&bounds, ns_string!("Width")),
            number(&bounds, ns_string!("Height")),
        ) else {
            continue;
        };
        for d in displays {
            let r = d.desktop_bounds;
            if r.contains(x + w / 2., y + h / 2.) {
                return (
                    (x - r.x).abs() < 2.
                        && (y - r.y).abs() < 2.
                        && (w - r.width).abs() < 2.
                        && (h - r.height).abs() < 2.,
                    Some(d.id),
                );
            }
        }
    }
    (false, None)
}
