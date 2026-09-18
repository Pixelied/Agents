use super::*;
use raw_window_handle::{RawDisplayHandle, Win32WindowHandle, WindowsDisplayHandle};
use std::num::NonZeroIsize;
const CLASS: PCWSTR = w!("InsectRealismNative0616");
const TRAY_MESSAGE: u32 = WM_APP + 41;
pub(super) struct WindowData {
    events: Events,
    overlay: bool,
    pub hotkey: Cell<i32>,
    pub utility: RefCell<UtilityState>,
    taskbar: u32,
}
pub(super) struct NativeWindow {
    pub hwnd: HWND,
    pub data: Box<WindowData>,
}
impl NativeWindow {
    pub fn new(
        events: Events,
        display: Option<&DisplaySurface>,
    ) -> Result<Rc<Self>, PlatformError> {
        unsafe {
            let instance = HINSTANCE(GetModuleHandleW(None).map_err(error)?.0);
            let wc = WNDCLASSW {
                lpfnWndProc: Some(procedure),
                hInstance: instance,
                lpszClassName: CLASS,
                hbrBackground: HBRUSH::default(),
                ..Default::default()
            };
            if RegisterClassW(&wc) == 0 && GetLastError() != ERROR_CLASS_ALREADY_EXISTS {
                return Err(error(windows::core::Error::from_thread()));
            }
            let mut data = Box::new(WindowData {
                events,
                overlay: display.is_some(),
                hotkey: Cell::new(0),
                utility: RefCell::new(UtilityState::default()),
                taskbar: RegisterWindowMessageW(w!("TaskbarCreated")),
            });
            let (ex, x, y, w, h) = if let Some(d) = display {
                (
                    WS_EX_NOACTIVATE
                        | WS_EX_TOOLWINDOW
                        | WS_EX_NOREDIRECTIONBITMAP
                        | WS_EX_LAYERED
                        | WS_EX_TRANSPARENT,
                    d.desktop_bounds.x as i32,
                    d.desktop_bounds.y as i32,
                    d.pixels.width as i32,
                    d.pixels.height as i32,
                )
            } else {
                (WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE, 0, 0, 1, 1)
            };
            let hwnd = CreateWindowExW(
                ex,
                CLASS,
                w!("Insect Realism"),
                WS_POPUP,
                x,
                y,
                w,
                h,
                None,
                None,
                Some(instance),
                Some((&mut *data as *mut WindowData).cast()),
            )
            .map_err(error)?;
            let native = Rc::new(Self { hwnd, data });
            // Normal compositor topmost ordering, set only once; never fight secure/protected surfaces.
            if display.is_some() {
                SetWindowPos(
                    hwnd,
                    Some(HWND_TOPMOST),
                    x,
                    y,
                    w,
                    h,
                    SWP_NOACTIVATE | SWP_HIDEWINDOW,
                )
                .map_err(error)?;
            }
            Ok(native)
        }
    }
}
impl Drop for NativeWindow {
    fn drop(&mut self) {
        unsafe {
            let _ = DestroyWindow(self.hwnd);
        }
    }
}
unsafe extern "system" fn procedure(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    // SAFETY: Callback receives a valid HWND/message. Box userdata is installed during NCCREATE,
    // remains alive through DestroyWindow, and is cleared during NCDESTROY. No borrowed OS pointer escapes.
    unsafe {
        if msg == WM_NCCREATE {
            if lparam.0 == 0 {
                return LRESULT(0);
            }
            let create = &*(lparam.0 as *const CREATESTRUCTW);
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, create.lpCreateParams as isize);
            return LRESULT(1);
        }
        let ptr = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *const WindowData;
        if msg == WM_NCDESTROY {
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, 0);
            return DefWindowProcW(hwnd, msg, wparam, lparam);
        }
        if ptr.is_null() {
            return DefWindowProcW(hwnd, msg, wparam, lparam);
        }
        let data = &*ptr;
        if crate::defer_native_close(msg) {
            emit(&data.events, PlatformEvent::QuitRequested);
            return LRESULT(0);
        }
        if data.overlay {
            match msg {
                WM_NCHITTEST => return LRESULT(HTTRANSPARENT as isize),
                WM_MOUSEACTIVATE => return LRESULT(MA_NOACTIVATE as isize),
                WM_ERASEBKGND => return LRESULT(1),
                WM_DPICHANGED => {
                    emit(&data.events, PlatformEvent::DisplaysChanged);
                    return LRESULT(0);
                }
                WM_PAINT => {
                    let mut ps = PAINTSTRUCT::default();
                    let _ = BeginPaint(hwnd, &mut ps);
                    let _ = EndPaint(hwnd, &ps);
                    return LRESULT(0);
                }
                _ => (),
            }
        } else {
            if msg == data.taskbar && data.taskbar != 0 {
                if let Ok(state) = data.utility.try_borrow() {
                    tray(hwnd, NIM_ADD, &state);
                }
                return LRESULT(0);
            }
            match msg {
                WM_HOTKEY if wparam.0 as i32 == data.hotkey.get() => {
                    emit(&data.events, PlatformEvent::PanicHotkey);
                    return LRESULT(0);
                }
                WM_DISPLAYCHANGE => {
                    emit(&data.events, PlatformEvent::DisplaysChanged);
                    return LRESULT(0);
                }
                WM_POWERBROADCAST => {
                    match wparam.0 as u32 {
                        PBT_APMSUSPEND => emit(&data.events, PlatformEvent::Suspend),
                        PBT_APMRESUMEAUTOMATIC | PBT_APMRESUMESUSPEND => {
                            emit(&data.events, PlatformEvent::Resume);
                            emit(&data.events, PlatformEvent::DisplaysChanged);
                        }
                        _ => (),
                    }
                    return LRESULT(1);
                }
                WM_WTSSESSION_CHANGE => {
                    match wparam.0 as u32 {
                        WTS_SESSION_LOCK
                        | WTS_SESSION_LOGOFF
                        | WTS_CONSOLE_DISCONNECT
                        | WTS_REMOTE_DISCONNECT => emit(&data.events, PlatformEvent::Suspend),
                        WTS_SESSION_UNLOCK | WTS_SESSION_LOGON | WTS_CONSOLE_CONNECT
                        | WTS_REMOTE_CONNECT => {
                            emit(&data.events, PlatformEvent::Resume);
                            emit(&data.events, PlatformEvent::DisplaysChanged);
                        }
                        _ => (),
                    }
                    return LRESULT(0);
                }
                TRAY_MESSAGE => {
                    match lparam.0 as u32 {
                        WM_RBUTTONUP | WM_CONTEXTMENU => show_menu(hwnd, data),
                        WM_LBUTTONDBLCLK => emit(
                            &data.events,
                            PlatformEvent::UtilityAction(UtilityAction::OpenSettings),
                        ),
                        _ => (),
                    }
                    return LRESULT(0);
                }
                WM_CLOSE => {
                    emit(&data.events, PlatformEvent::QuitRequested);
                    return LRESULT(0);
                }
                _ => (),
            }
        }
        DefWindowProcW(hwnd, msg, wparam, lparam)
    }
}
fn show_menu(hwnd: HWND, data: &WindowData) {
    // SAFETY: Menu is created and destroyed synchronously on the window's UI thread. Labels live across AppendMenuW calls.
    unsafe {
        let Ok(menu) = CreatePopupMenu() else {
            return;
        };
        let state = data
            .utility
            .try_borrow()
            .map(|x| x.clone())
            .unwrap_or_default();
        for (id, label) in [
            (
                1,
                if state.visible {
                    "Hide All"
                } else {
                    "Show Creatures"
                },
            ),
            (2, if state.paused { "Resume" } else { "Pause" }),
            (3, "Settings..."),
            (10, "Realistic"),
            (11, "Light Infestation"),
            (12, "Heavy Infestation"),
            (13, "Nightmare"),
            (
                4,
                if state.launch_at_login {
                    "Disable Launch at Login"
                } else {
                    "Launch at Login"
                },
            ),
            (5, "Quit"),
        ] {
            let label = wide(label);
            let _ = AppendMenuW(menu, MF_STRING, id, PCWSTR(label.as_ptr()));
        }
        let mut pos = POINT::default();
        let _ = GetCursorPos(&mut pos);
        let _ = SetForegroundWindow(hwnd); // user explicitly opened tray menu; overlays never activate
        let id = TrackPopupMenu(
            menu,
            TPM_RETURNCMD | TPM_NONOTIFY | TPM_RIGHTBUTTON,
            pos.x,
            pos.y,
            None,
            hwnd,
            None,
        )
        .0;
        let _ = DestroyMenu(menu);
        let _ = PostMessageW(Some(hwnd), WM_NULL, WPARAM(0), LPARAM(0));
        let action = match id {
            1 => UtilityAction::ToggleVisible,
            2 => UtilityAction::TogglePause,
            3 => UtilityAction::OpenSettings,
            4 => UtilityAction::ToggleLaunchAtLogin,
            5 => UtilityAction::Quit,
            10 => UtilityAction::SetPreset("realistic".into()),
            11 => UtilityAction::SetPreset("light".into()),
            12 => UtilityAction::SetPreset("heavy".into()),
            13 => UtilityAction::SetPreset("nightmare".into()),
            _ => return,
        };
        emit(&data.events, PlatformEvent::UtilityAction(action));
    }
}
pub(super) fn tray(hwnd: HWND, operation: NOTIFY_ICON_MESSAGE, state: &UtilityState) -> bool {
    let mut n = NOTIFYICONDATAW {
        cbSize: std::mem::size_of::<NOTIFYICONDATAW>() as u32,
        hWnd: hwnd,
        uID: 1,
        uFlags: NIF_MESSAGE | NIF_ICON | NIF_TIP,
        uCallbackMessage: TRAY_MESSAGE,
        ..Default::default()
    };
    unsafe {
        n.hIcon = LoadIconW(None, IDI_APPLICATION).unwrap_or_default();
    }
    let tooltip = wide(
        state
            .diagnostic
            .as_deref()
            .unwrap_or("Insect Realism - double-click for Settings"),
    );
    let len = tooltip.len().min(n.szTip.len() - 1);
    n.szTip[..len].copy_from_slice(&tooltip[..len]);
    unsafe { Shell_NotifyIconW(operation, &n).as_bool() }
}
pub(super) struct Overlay {
    id: DisplayId,
    native: Rc<NativeWindow>,
    gate: SafetyGate,
}
impl Overlay {
    pub fn new(events: Events, d: &DisplaySurface) -> Result<Self, PlatformError> {
        let this = Self {
            id: d.id,
            native: NativeWindow::new(events, Some(d))?,
            gate: SafetyGate::default(),
        };
        this.verify_input_passthrough()?;
        Ok(this)
    }
}
impl OverlayWindow for Overlay {
    fn display_id(&self) -> DisplayId {
        self.id
    }
    fn safety_state(&self) -> OverlaySafetyState {
        self.gate.state()
    }
    fn verify_input_passthrough(&self) -> Result<(), PlatformError> {
        let flags =
            WINDOW_EX_STYLE(unsafe { GetWindowLongPtrW(self.native.hwnd, GWL_EXSTYLE) } as u32);
        let style = WINDOW_STYLE(unsafe { GetWindowLongPtrW(self.native.hwnd, GWL_STYLE) } as u32);
        let proof = NativeSafetyProof {
            transparent: flags.contains(WS_EX_NOREDIRECTIONBITMAP | WS_EX_LAYERED),
            non_activating: flags.contains(WS_EX_NOACTIVATE)
                && style.contains(WS_POPUP)
                && !style.contains(WS_THICKFRAME),
            click_through: flags.contains(WS_EX_TRANSPARENT | WS_EX_LAYERED),
            not_task_switcher: flags.contains(WS_EX_TOOLWINDOW) && !flags.contains(WS_EX_APPWINDOW),
        };
        let result = self.gate.verify(proof);
        if result.is_err() {
            unsafe {
                let _ = ShowWindow(self.native.hwnd, SW_HIDE);
            }
        }
        result
    }
    fn set_visible(&self, visible: bool) -> Result<(), PlatformError> {
        if visible {
            self.verify_input_passthrough()?;
            self.gate.authorize_show()?;
            unsafe {
                let _ = ShowWindow(self.native.hwnd, SW_SHOWNOACTIVATE);
            }
        } else {
            unsafe {
                let _ = ShowWindow(self.native.hwnd, SW_HIDE);
            }
            self.gate.hide();
        }
        Ok(())
    }
    fn is_visible(&self) -> bool {
        self.gate.is_visible() && unsafe { IsWindowVisible(self.native.hwnd).as_bool() }
    }
    fn mark_unsafe(&self) {
        unsafe {
            let _ = ShowWindow(self.native.hwnd, SW_HIDE);
        }
        self.gate.invalidate();
    }
    fn surface_source(&self) -> Result<SurfaceSource, PlatformError> {
        self.verify_input_passthrough()?;
        let hwnd =
            NonZeroIsize::new(self.native.hwnd.0 as isize).ok_or_else(|| error("null HWND"))?;
        let mut h = Win32WindowHandle::new(hwnd);
        h.hinstance =
            NonZeroIsize::new(unsafe { GetWindowLongPtrW(self.native.hwnd, GWLP_HINSTANCE) });
        // SAFETY: Rc holds NativeWindow/Box callback data until all GPU sources drop; HWND is only destroyed in NativeWindow::drop.
        Ok(unsafe {
            SurfaceSource::from_raw(
                RawDisplayHandle::Windows(WindowsDisplayHandle::new()),
                RawWindowHandle::Win32(h),
                self.native.clone(),
            )
        })
    }
}
impl Drop for Overlay {
    fn drop(&mut self) {
        unsafe {
            let _ = ShowWindow(self.native.hwnd, SW_HIDE);
        }
    }
}
