//! AppKit ownership and callbacks stay on the native UI thread. No screen capture or injection.
mod display;
mod hotkey;
mod overlay;
mod utility;
use display_model::{DisplayId, DisplaySurface};
use objc2::rc::Retained;
use objc2_app_kit::{
    NSAlert, NSApplication, NSApplicationActivationPolicy, NSView, NSWindow, NSWorkspace,
};
use objc2_foundation::{MainThreadMarker, NSString};
use platform_api::*;
use raw_window_handle::{HasWindowHandle, RawWindowHandle};
use std::{cell::RefCell, io::Write, path::PathBuf, rc::Rc};

pub(super) type Events = Rc<RefCell<EventQueue>>;
pub(super) fn emit(queue: &Events, event: PlatformEvent) {
    // Callbacks are synchronous UI-thread events; avoid unwinding at the FFI boundary.
    if let Ok(mut q) = queue.try_borrow_mut() {
        q.push(event);
    }
}
pub(super) fn native_error(e: impl std::fmt::Display) -> PlatformError {
    PlatformError::Native(e.to_string())
}
pub struct MacAdapter {
    mtm: MainThreadMarker,
    events: Events,
    hotkey: hotkey::Hotkey,
    utility: utility::Utility,
    recent: RefCell<Vec<AppIdentity>>,
}
impl MacAdapter {
    pub fn new() -> Result<Self, PlatformError> {
        let mtm = MainThreadMarker::new().ok_or(PlatformError::WrongThread)?;
        NSApplication::sharedApplication(mtm)
            .setActivationPolicy(NSApplicationActivationPolicy::Accessory);
        let events = Rc::new(RefCell::new(EventQueue::default()));
        let utility = utility::Utility::new(mtm, events.clone())?;
        let hotkey = hotkey::Hotkey::new(events.clone())?;
        Ok(Self {
            mtm,
            events,
            hotkey,
            utility,
            recent: RefCell::new(Vec::new()),
        })
    }
    fn native_settings_window(
        &self,
        window: &dyn HasWindowHandle,
    ) -> Result<Retained<NSWindow>, PlatformError> {
        let h = window.window_handle().map_err(native_error)?;
        let RawWindowHandle::AppKit(h) = h.as_raw() else {
            return Err(PlatformError::Unsupported("not an AppKit view"));
        };
        // SAFETY: HasWindowHandle guards a live AppKit NSView during this call; UI thread marker is held.
        let view = unsafe { h.ns_view.cast::<NSView>().as_ref() };
        view.window()
            .ok_or(PlatformError::Native("settings view has no window".into()))
    }
}
impl PlatformAdapter for MacAdapter {
    fn enumerate_displays(&self) -> Result<Vec<DisplaySurface>, PlatformError> {
        display::enumerate(self.mtm)
    }
    fn create_overlay(
        &mut self,
        d: &DisplaySurface,
    ) -> Result<Box<dyn OverlayWindow>, PlatformError> {
        Ok(Box::new(overlay::Overlay::new(self.mtm, d)?))
    }
    fn register_panic_hotkey(&mut self, binding: &str) -> Result<(), PlatformError> {
        self.hotkey.register(binding)
    }
    fn set_launch_at_login(&self, enabled: bool) -> Result<(), PlatformError> {
        let home =
            std::env::var_os("HOME").ok_or(PlatformError::Native("HOME is unavailable".into()))?;
        let dir = PathBuf::from(home).join("Library/LaunchAgents");
        let path = dir.join("com.pixelied.insect-realism.plist");
        if !enabled {
            match std::fs::remove_file(&path) {
                Ok(()) => (),
                Err(e) if e.kind() == std::io::ErrorKind::NotFound => (),
                Err(e) => return Err(native_error(e)),
            }
            return Ok(());
        }
        let exe = std::env::current_exe().map_err(native_error)?;
        let exe = exe.to_str().ok_or(PlatformError::Native(
            "executable path is not valid UTF-8".into(),
        ))?;
        if !exe.contains(".app/Contents/MacOS/") || exe.contains("AppTranslocation") {
            return Err(PlatformError::Native(
                "Install the .app in a stable location before enabling launch at login".into(),
            ));
        }
        std::fs::create_dir_all(&dir).map_err(native_error)?;
        let mut file = tempfile::NamedTempFile::new_in(&dir).map_err(native_error)?;
        file.write_all(crate::login_plist(exe).as_bytes())
            .map_err(native_error)?;
        file.as_file().sync_all().map_err(native_error)?;
        file.persist(path).map_err(native_error)?;
        Ok(())
    }
    fn foreground_context(&self) -> Result<Option<ForegroundContext>, PlatformError> {
        let Some(app) = NSWorkspace::sharedWorkspace().frontmostApplication() else {
            return Ok(None);
        };
        let stable_id = app.bundleIdentifier().map(|s| s.to_string()).or_else(|| {
            app.executableURL()
                .and_then(|u| u.path())
                .map(|s| s.to_string())
        });
        let Some(stable_id) = stable_id else {
            return Ok(None);
        };
        let identity = AppIdentity {
            stable_id,
            display_name: app
                .localizedName()
                .map(|s| s.to_string())
                .unwrap_or_else(|| "Application".into()),
        };
        let mut recent = self.recent.borrow_mut();
        if !recent.iter().any(|x| x.stable_id == identity.stable_id) {
            if recent.len() >= 32 {
                recent.remove(0);
            }
            recent.push(identity.clone());
        }
        let (fullscreen, display) =
            display::foreground_geometry(app.processIdentifier(), &self.enumerate_displays()?);
        Ok(Some(ForegroundContext {
            app: identity,
            fullscreen,
            display,
        }))
    }
    fn recent_apps(&self) -> Result<Vec<AppIdentity>, PlatformError> {
        Ok(self.recent.borrow().clone())
    }
    fn poll_events(&mut self) -> Result<Vec<PlatformEvent>, PlatformError> {
        Ok(self.events.borrow_mut().drain())
    }
    fn cursor_position(&self) -> Result<Option<DesktopCursor>, PlatformError> {
        let p = objc2_app_kit::NSEvent::mouseLocation();
        Ok(Some(DesktopCursor {
            x: p.x,
            y: display::primary_top(self.mtm) - p.y,
        }))
    }
    fn set_utility_state(&mut self, state: &UtilityState) {
        self.utility.update(state);
    }
    fn window_display(
        &self,
        window: &dyn HasWindowHandle,
    ) -> Result<Option<DisplayId>, PlatformError> {
        let window = self.native_settings_window(window)?;
        let Some(screen) = window.screen() else {
            return Ok(None);
        };
        let frame = window.frame();
        let bounds = screen.frame();
        let outer = display_model::DesktopRect {
            x: bounds.origin.x,
            y: bounds.origin.y,
            width: bounds.size.width,
            height: bounds.size.height,
        };
        let inner = display_model::DesktopRect {
            x: frame.origin.x,
            y: frame.origin.y,
            width: frame.size.width,
            height: frame.size.height,
        };
        Ok(contains_desktop_rect(outer, inner)
            .then(|| display_id(&display::fingerprint(display::screen_id(&screen)))))
    }

    fn move_settings_to_display(
        &self,
        window: &dyn HasWindowHandle,
        id: DisplayId,
    ) -> Result<(), PlatformError> {
        let window = self.native_settings_window(window)?;
        let screen = display::screen_for(self.mtm, id)
            .ok_or(PlatformError::Native("display was removed".into()))?;
        let r = screen.visibleFrame();
        let size = window.frame().size;
        window.setFrameOrigin(objc2_foundation::NSPoint::new(
            r.origin.x + (r.size.width - size.width).max(0.) / 2.,
            r.origin.y + (r.size.height - size.height).max(0.) / 2.,
        ));
        Ok(())
    }
    fn present_diagnostic(&self, message: &str) {
        let alert = NSAlert::new(self.mtm);
        alert.setMessageText(&NSString::from_str("Insect Realism"));
        alert.setInformativeText(&NSString::from_str(message));
        alert.runModal();
    }
}
