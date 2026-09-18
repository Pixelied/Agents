use platform_api::*;
#[test]
fn created_overlay_is_not_authorized() {
    let s = SafetyGate::default();
    assert!(!s.is_visible());
    assert!(s.authorize_show().is_err());
}
#[test]
fn every_missing_native_guarantee_is_fail_closed() {
    for missing in 0..4 {
        let mut p = NativeSafetyProof {
            transparent: true,
            non_activating: true,
            click_through: true,
            not_task_switcher: true,
        };
        match missing {
            0 => p.transparent = false,
            1 => p.non_activating = false,
            2 => p.click_through = false,
            _ => p.not_task_switcher = false,
        };
        let s = SafetyGate::default();
        assert!(s.verify(p).is_err());
        assert_eq!(s.state(), OverlaySafetyState::HiddenUnsafe);
        assert!(s.authorize_show().is_err());
    }
}
#[test]
fn verified_overlay_can_show_but_failure_revokes_it() {
    let s = SafetyGate::default();
    s.verify(NativeSafetyProof {
        transparent: true,
        non_activating: true,
        click_through: true,
        not_task_switcher: true,
    })
    .unwrap();
    s.authorize_show().unwrap();
    assert!(s.is_visible());
    s.invalidate();
    assert!(!s.is_visible());
    assert!(s.authorize_show().is_err());
}
#[test]
fn hide_is_not_loss_of_safety() {
    let s = SafetyGate::default();
    s.verify(NativeSafetyProof {
        transparent: true,
        non_activating: true,
        click_through: true,
        not_task_switcher: true,
    })
    .unwrap();
    s.authorize_show().unwrap();
    s.hide();
    assert!(!s.is_visible());
    s.authorize_show().unwrap();
}
#[test]
fn panic_key_requires_modifiers_and_supported_keys() {
    assert!(HotkeyBinding::parse("H").is_err());
    assert!(HotkeyBinding::parse("Ctrl+Ctrl+H").is_err());
    assert!(HotkeyBinding::parse("Ctrl+F25").is_err());
    assert!(HotkeyBinding::parse("Ctrl+Alt+Shift+H").is_ok());
    assert!(HotkeyBinding::parse("Command+Option+Escape").is_ok());
    assert!(HotkeyBinding::parse("Ctrl+unknown").is_err());
}
#[test]
fn source_owner_outlives_original_handle() {
    use raw_window_handle::{
        RawDisplayHandle, RawWindowHandle, Win32WindowHandle, WindowsDisplayHandle,
    };
    use std::cell::Cell;
    use std::rc::Rc;
    struct Owner(Rc<Cell<bool>>);
    impl Drop for Owner {
        fn drop(&mut self) {
            self.0.set(true);
        }
    }
    let dropped = Rc::new(Cell::new(false));
    let owner = Rc::new(Owner(dropped.clone()));
    let weak = Rc::downgrade(&owner);
    let raw = Win32WindowHandle::new(std::num::NonZeroIsize::new(1).unwrap());
    // Test-only non-dereferenced sentinel handle. No OS/GPU operation is performed.
    let source = unsafe {
        SurfaceSource::from_raw(
            RawDisplayHandle::Windows(WindowsDisplayHandle::new()),
            RawWindowHandle::Win32(raw),
            owner.clone(),
        )
    };
    drop(owner);
    assert!(!dropped.get());
    let second = source.clone();
    drop(source);
    assert!(weak.upgrade().is_some());
    drop(second);
    assert!(dropped.get());
}
#[test]
fn calibration_window_must_fit_entirely_on_one_physical_surface() {
    use display_model::DesktopRect;
    let panel = DesktopRect {
        x: -1000.,
        y: 0.,
        width: 1000.,
        height: 800.,
    };
    assert!(platform_api::contains_desktop_rect(
        panel,
        DesktopRect {
            x: -900.,
            y: 100.,
            width: 600.,
            height: 600.
        }
    ));
    assert!(!platform_api::contains_desktop_rect(
        panel,
        DesktopRect {
            x: -400.,
            y: 100.,
            width: 600.,
            height: 600.
        }
    ));
    assert!(!platform_api::contains_desktop_rect(
        panel,
        DesktopRect {
            x: -900.,
            y: 100.,
            width: f64::NAN,
            height: 600.
        }
    ));
}
