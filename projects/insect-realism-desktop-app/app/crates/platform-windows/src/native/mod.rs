//! Native Win32 windows and their GPU source guards remain on the creating UI thread.
mod display;
mod window;
use display_model::{DisplayId, DisplaySurface};
use platform_api::*;
use raw_window_handle::{HasWindowHandle, RawWindowHandle};
use std::{
    cell::{Cell, RefCell},
    rc::Rc,
};
use windows::{
    Win32::{
        Foundation::*,
        Graphics::Gdi::*,
        System::{LibraryLoader::GetModuleHandleW, Registry::*, RemoteDesktop::*, Threading::*},
        UI::{HiDpi::*, Input::KeyboardAndMouse::*, Shell::*, WindowsAndMessaging::*},
    },
    core::{PCWSTR, PWSTR, w},
};

pub(super) type Events = Rc<RefCell<EventQueue>>;
pub(super) fn error(e: impl std::fmt::Display) -> PlatformError {
    PlatformError::Native(e.to_string())
}
pub(super) fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(Some(0)).collect()
}
pub(super) fn text(s: &[u16]) -> String {
    String::from_utf16_lossy(&s[..s.iter().position(|x| *x == 0).unwrap_or(s.len())])
}
pub(super) fn emit(queue: &Events, event: PlatformEvent) {
    // Callbacks are synchronous UI-thread events; avoid unwinding at the FFI boundary.
    if let Ok(mut q) = queue.try_borrow_mut() {
        q.push(event);
    }
}
/// Call before winit/event-loop initialization or creating any window.
pub fn enable_per_monitor_v2() -> Result<(), PlatformError> {
    // SAFETY: Process policy set once before any HWND creation. Existing PMv2 policy is acceptable.
    unsafe {
        if SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2).is_err()
            && !AreDpiAwarenessContextsEqual(
                GetThreadDpiAwarenessContext(),
                DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
            )
            .as_bool()
        {
            return Err(PlatformError::Native(
                "Per-monitor-v2 DPI awareness must be established before window creation".into(),
            ));
        }
    }
    Ok(())
}
pub struct WindowsAdapter {
    thread: u32,
    events: Events,
    utility: Rc<window::NativeWindow>,
    binding: Option<HotkeyBinding>,
    hotkey: i32,
    recent: RefCell<Vec<AppIdentity>>,
}
impl WindowsAdapter {
    pub fn new() -> Result<Self, PlatformError> {
        enable_per_monitor_v2()?;
        let events = Rc::new(RefCell::new(EventQueue::default()));
        let utility = window::NativeWindow::new(events.clone(), None)?;
        unsafe {
            WTSRegisterSessionNotification(utility.hwnd, NOTIFY_FOR_THIS_SESSION).map_err(error)?;
        }
        if !window::tray(utility.hwnd, NIM_ADD, &UtilityState::default()) {
            return Err(PlatformError::Native(
                "notification-area icon could not be created".into(),
            ));
        }
        Ok(Self {
            thread: unsafe { GetCurrentThreadId() },
            events,
            utility,
            binding: None,
            hotkey: 0,
            recent: RefCell::new(Vec::new()),
        })
    }
    fn on_thread(&self) -> Result<(), PlatformError> {
        if unsafe { GetCurrentThreadId() } != self.thread {
            Err(PlatformError::WrongThread)
        } else {
            Ok(())
        }
    }
    fn settings_hwnd(&self, window: &dyn HasWindowHandle) -> Result<HWND, PlatformError> {
        self.on_thread()?;
        match window.window_handle().map_err(error)?.as_raw() {
            RawWindowHandle::Win32(h) => Ok(HWND(h.hwnd.get() as *mut _)),
            _ => Err(PlatformError::Unsupported("not a Win32 settings window")),
        }
    }
}
impl PlatformAdapter for WindowsAdapter {
    fn enumerate_displays(&self) -> Result<Vec<DisplaySurface>, PlatformError> {
        self.on_thread()?;
        display::enumerate()
    }
    fn create_overlay(
        &mut self,
        d: &DisplaySurface,
    ) -> Result<Box<dyn OverlayWindow>, PlatformError> {
        self.on_thread()?;
        Ok(Box::new(window::Overlay::new(self.events.clone(), d)?))
    }
    fn register_panic_hotkey(&mut self, text: &str) -> Result<(), PlatformError> {
        self.on_thread()?;
        let binding = HotkeyBinding::parse(text)?;
        if self.binding == Some(binding) {
            return Ok(());
        }
        let key = crate::virtual_key(binding.key)
            .ok_or(PlatformError::Hotkey("unsupported shortcut key".into()))?;
        let mut modifiers = MOD_NOREPEAT;
        if binding.control {
            modifiers |= MOD_CONTROL;
        }
        if binding.alt {
            modifiers |= MOD_ALT;
        }
        if binding.shift {
            modifiers |= MOD_SHIFT;
        }
        if binding.super_key {
            modifiers |= MOD_WIN;
        }
        // Alternate application-owned IDs. A failed replacement leaves the previous shortcut active.
        let id = if self.hotkey == 0x4941 {
            0x4942
        } else {
            0x4941
        };
        unsafe { RegisterHotKey(Some(self.utility.hwnd), id, modifiers, key) }.map_err(|e| {
            PlatformError::Hotkey(format!(
                "shortcut unavailable; previous binding retained: {e}"
            ))
        })?;
        if self.hotkey != 0 {
            let _ = unsafe { UnregisterHotKey(Some(self.utility.hwnd), self.hotkey) };
        }
        self.hotkey = id;
        self.utility.data.hotkey.set(id);
        self.binding = Some(binding);
        Ok(())
    }
    fn set_launch_at_login(&self, enabled: bool) -> Result<(), PlatformError> {
        self.on_thread()?;
        let mut key = HKEY::default();
        // SAFETY: Only the current user's conventional Run value is modified; registry handle is always closed.
        unsafe {
            RegCreateKeyExW(
                HKEY_CURRENT_USER,
                w!("Software\\Microsoft\\Windows\\CurrentVersion\\Run"),
                None,
                PCWSTR::null(),
                REG_OPTION_NON_VOLATILE,
                KEY_SET_VALUE,
                None,
                &mut key,
                None,
            )
            .ok()
            .map_err(error)?;
            let result = if enabled {
                let path = std::env::current_exe().map_err(error);
                path.and_then(|p| {
                    p.to_str()
                        .ok_or_else(|| error("executable path cannot be encoded"))
                        .and_then(crate::autostart_command)
                })
                .and_then(|p| {
                    let bytes: Vec<u8> = wide(&p).into_iter().flat_map(u16::to_le_bytes).collect();
                    RegSetValueExW(key, w!("InsectRealism"), None, REG_SZ, Some(&bytes))
                        .ok()
                        .map_err(error)
                })
            } else {
                let status = RegDeleteValueW(key, w!("InsectRealism"));
                if status == ERROR_FILE_NOT_FOUND {
                    Ok(())
                } else {
                    status.ok().map_err(error)
                }
            };
            let _ = RegCloseKey(key);
            result
        }
    }
    fn foreground_context(&self) -> Result<Option<ForegroundContext>, PlatformError> {
        self.on_thread()?;
        unsafe {
            let hwnd = GetForegroundWindow();
            if hwnd.is_invalid() {
                return Ok(None);
            }
            let mut pid = 0;
            GetWindowThreadProcessId(hwnd, Some(&mut pid));
            let Ok(process) = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) else {
                return Ok(None);
            };
            let mut buffer = vec![0u16; 32768];
            let mut len = buffer.len() as u32;
            let query = QueryFullProcessImageNameW(
                process,
                PROCESS_NAME_FORMAT(0),
                PWSTR(buffer.as_mut_ptr()),
                &mut len,
            );
            let _ = CloseHandle(process);
            if query.is_err() {
                return Ok(None);
            }
            let path = String::from_utf16_lossy(&buffer[..len as usize]);
            let name = path.rsplit(['\\', '/']).next().unwrap_or(&path).to_string();
            let app = AppIdentity {
                stable_id: path.to_lowercase(),
                display_name: name,
            };
            let mut recent = self.recent.borrow_mut();
            if !recent.iter().any(|x| x.stable_id == app.stable_id) {
                if recent.len() >= 32 {
                    recent.remove(0);
                }
                recent.push(app.clone());
            }
            let mut bounds = RECT::default();
            GetWindowRect(hwnd, &mut bounds).map_err(error)?;
            let monitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
            let surface = display::surface(monitor)?;
            let r = surface.desktop_bounds;
            let fullscreen = (f64::from(bounds.left) - r.x).abs() <= 2.
                && (f64::from(bounds.top) - r.y).abs() <= 2.
                && (f64::from(bounds.right) - r.right()).abs() <= 2.
                && (f64::from(bounds.bottom) - r.bottom()).abs() <= 2.;
            Ok(Some(ForegroundContext {
                app,
                fullscreen,
                display: Some(surface.id),
            }))
        }
    }
    fn recent_apps(&self) -> Result<Vec<AppIdentity>, PlatformError> {
        Ok(self.recent.borrow().clone())
    }
    fn poll_events(&mut self) -> Result<Vec<PlatformEvent>, PlatformError> {
        self.on_thread()?;
        Ok(self.events.borrow_mut().drain())
    }
    fn cursor_position(&self) -> Result<Option<DesktopCursor>, PlatformError> {
        self.on_thread()?;
        let mut p = POINT::default();
        unsafe { GetCursorPos(&mut p) }.map_err(error)?;
        Ok(Some(DesktopCursor {
            x: f64::from(p.x),
            y: f64::from(p.y),
        }))
    }
    fn set_utility_state(&mut self, state: &UtilityState) {
        *self.utility.data.utility.borrow_mut() = state.clone();
        window::tray(self.utility.hwnd, NIM_MODIFY, state);
    }
    fn window_display(
        &self,
        window: &dyn HasWindowHandle,
    ) -> Result<Option<DisplayId>, PlatformError> {
        let hwnd = self.settings_hwnd(window)?;
        let display =
            display::surface(unsafe { MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) })?;
        let mut rect = RECT::default();
        // SAFETY: settings_hwnd validated the borrowed live HWND for this call.
        unsafe { GetWindowRect(hwnd, &mut rect) }.map_err(error)?;
        let window = display_model::DesktopRect {
            x: f64::from(rect.left),
            y: f64::from(rect.top),
            width: f64::from(rect.right - rect.left),
            height: f64::from(rect.bottom - rect.top),
        };
        Ok(contains_desktop_rect(display.desktop_bounds, window).then_some(display.id))
    }

    fn move_settings_to_display(
        &self,
        window: &dyn HasWindowHandle,
        id: DisplayId,
    ) -> Result<(), PlatformError> {
        let hwnd = self.settings_hwnd(window)?;
        let display = self
            .enumerate_displays()?
            .into_iter()
            .find(|d| d.id == id)
            .ok_or_else(|| error("display removed"))?;
        let mut r = RECT::default();
        unsafe { GetWindowRect(hwnd, &mut r) }.map_err(error)?;
        let d = display.desktop_bounds;
        unsafe {
            SetWindowPos(
                hwnd,
                None,
                (d.x + (d.width - f64::from(r.right - r.left)).max(0.) / 2.) as i32,
                (d.y + (d.height - f64::from(r.bottom - r.top)).max(0.) / 2.) as i32,
                0,
                0,
                SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE,
            )
        }
        .map_err(error)
    }
    fn present_diagnostic(&self, message: &str) {
        let text = wide(message);
        unsafe {
            MessageBoxW(
                None,
                PCWSTR(text.as_ptr()),
                w!("Insect Realism"),
                MB_OK | MB_ICONWARNING,
            )
        };
    }
}
impl Drop for WindowsAdapter {
    fn drop(&mut self) {
        unsafe {
            if self.hotkey != 0 {
                let _ = UnregisterHotKey(Some(self.utility.hwnd), self.hotkey);
            }
            let _ = WTSUnRegisterSessionNotification(self.utility.hwnd);
        }
        window::tray(self.utility.hwnd, NIM_DELETE, &UtilityState::default());
    }
}

/// Installer-only cleanup. No window, registry enumeration, renderer, or other user's key is touched.
/// An absent value is already clean. Permission errors propagate to the installer instead of
/// silently claiming that startup cleanup succeeded.
pub fn clear_startup_entry() -> Result<(), PlatformError> {
    // SAFETY: Fixed current-user key/value names; RegDeleteKeyValueW borrows constant UTF-16 strings.
    let result = unsafe {
        RegDeleteKeyValueW(
            HKEY_CURRENT_USER,
            w!("Software\\Microsoft\\Windows\\CurrentVersion\\Run"),
            w!("InsectRealism"),
        )
    };
    if result == ERROR_FILE_NOT_FOUND || result == ERROR_PATH_NOT_FOUND {
        Ok(())
    } else {
        result.ok().map_err(error)
    }
}
