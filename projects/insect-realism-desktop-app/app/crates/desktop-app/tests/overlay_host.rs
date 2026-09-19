use desktop_app::overlay_host::OverlayHost;
use display_model::DisplayId;
use platform_api::*;
use std::{cell::RefCell, rc::Rc};
type Log = Rc<RefCell<Vec<&'static str>>>;
struct Window {
    gate: SafetyGate,
    log: Log,
}
impl OverlayWindow for Window {
    fn display_id(&self) -> DisplayId {
        DisplayId(1)
    }
    fn safety_state(&self) -> OverlaySafetyState {
        self.gate.state()
    }
    fn verify_input_passthrough(&self) -> Result<(), PlatformError> {
        self.log.borrow_mut().push("verify");
        self.gate.verify(NativeSafetyProof {
            transparent: true,
            non_activating: true,
            click_through: true,
            not_task_switcher: true,
        })
    }
    fn set_visible(&self, v: bool) -> Result<(), PlatformError> {
        self.log.borrow_mut().push(if v { "show" } else { "hide" });
        if v {
            self.gate.authorize_show()
        } else {
            self.gate.hide();
            Ok(())
        }
    }
    fn is_visible(&self) -> bool {
        self.gate.is_visible()
    }
    fn mark_unsafe(&self) {
        self.log.borrow_mut().push("unsafe-hidden");
        self.gate.invalidate();
    }
    fn surface_source(&self) -> Result<SurfaceSource, PlatformError> {
        Err(PlatformError::Unsupported(
            "test backend uses no native handle",
        ))
    }
}
impl Drop for Window {
    fn drop(&mut self) {
        self.log.borrow_mut().push("window-drop");
    }
}
struct Surface(Log);
impl Drop for Surface {
    fn drop(&mut self) {
        self.0.borrow_mut().push("surface-drop");
    }
}
#[test]
fn visibility_waits_for_surface_creation_and_native_reverification() {
    let log = Log::default();
    let window = Box::new(Window {
        gate: SafetyGate::default(),
        log: log.clone(),
    });
    let host = OverlayHost::new(window, |_: &dyn OverlayWindow| {
        log.borrow_mut().push("create");
        Ok::<_, String>(Surface(log.clone()))
    })
    .unwrap();
    host.set_visible(true).unwrap();
    let events = log.borrow();
    let create = events.iter().position(|x| *x == "create").unwrap();
    let show = events.iter().position(|x| *x == "show").unwrap();
    assert!(create < show);
    assert!(events[create + 1..show].contains(&"verify"));
}
#[test]
fn device_loss_hides_before_dropping_surface_and_cannot_show_without_recovery() {
    let log = Log::default();
    let window = Box::new(Window {
        gate: SafetyGate::default(),
        log: log.clone(),
    });
    let mut host = OverlayHost::new(window, |_: &dyn OverlayWindow| {
        Ok::<_, String>(Surface(log.clone()))
    })
    .unwrap();
    host.invalidate();
    assert!(!host.safe());
    assert!(host.set_visible(true).is_err());
    let events = log.borrow();
    assert!(
        events.iter().position(|e| *e == "unsafe-hidden").unwrap()
            < events.iter().position(|e| *e == "surface-drop").unwrap()
    );
}
#[test]
fn failed_gpu_creation_never_shows_the_native_window() {
    let log = Log::default();
    let window = Box::new(Window {
        gate: SafetyGate::default(),
        log: log.clone(),
    });
    let result = OverlayHost::<Surface>::new(window, |_: &dyn OverlayWindow| {
        Err::<Surface, _>("device failed")
    });
    assert!(result.is_err());
    assert!(log.borrow().contains(&"unsafe-hidden"));
    assert!(!log.borrow().contains(&"show"));
}

#[test]
fn hidden_native_overlay_submits_no_render_work() {
    let log = Log::default();
    let window = Box::new(Window {
        gate: SafetyGate::default(),
        log: log.clone(),
    });
    let mut host = OverlayHost::new(window, |_: &dyn OverlayWindow| {
        Ok::<_, String>(Surface(log.clone()))
    })
    .unwrap();
    let mut draws = 0;
    host.present_if_visible(|_| {
        draws += 1;
        Ok::<_, String>(())
    })
    .unwrap();
    assert_eq!(draws, 0);
    host.set_visible(true).unwrap();
    host.present_if_visible(|_| {
        draws += 1;
        Ok::<_, String>(())
    })
    .unwrap();
    assert_eq!(draws, 1);
}
#[test]
fn render_failure_invalidates_native_visibility_before_returning() {
    let log = Log::default();
    let window = Box::new(Window {
        gate: SafetyGate::default(),
        log: log.clone(),
    });
    let mut host = OverlayHost::new(window, |_: &dyn OverlayWindow| {
        Ok::<_, String>(Surface(log.clone()))
    })
    .unwrap();
    host.set_visible(true).unwrap();
    assert!(host.present_if_visible(|_| Err::<(), _>("lost")).is_err());
    assert!(!host.safe());
    assert!(!host.is_visible());
}
#[test]
fn gpu_resources_drop_before_native_owners() {
    let log = Log::default();
    let window = Box::new(Window {
        gate: SafetyGate::default(),
        log: log.clone(),
    });
    let host = OverlayHost::new(window, |_: &dyn OverlayWindow| {
        Ok::<_, String>(Surface(log.clone()))
    })
    .unwrap();
    drop(host);
    let events = log.borrow();
    assert!(
        events.iter().position(|e| *e == "surface-drop").unwrap()
            < events.iter().position(|e| *e == "window-drop").unwrap()
    );
}
