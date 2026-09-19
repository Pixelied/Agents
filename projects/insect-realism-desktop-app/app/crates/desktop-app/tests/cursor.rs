use desktop_app::cursor::CursorTracker;
use display_model::*;
use platform_api::DesktopCursor;
use std::time::Duration;
fn d() -> DisplaySurface {
    DisplaySurface {
        id: DisplayId(1),
        fingerprint: "retina".into(),
        name: "Test".into(),
        pixels: PixelSize {
            width: 2000,
            height: 1000,
        },
        desktop_bounds: DesktopRect {
            x: -1000.,
            y: 0.,
            width: 1000.,
            height: 500.,
        },
        scale_factor: 2.,
        refresh_hz: 120.,
        rotation_deg: 0,
        calibration: DisplayCalibration::resolve(None, Some(0.1)).unwrap(),
    }
}
#[test]
fn cursor_velocity_uses_physical_millimeters_not_logical_dpi() {
    let mut t = CursorTracker::default();
    let displays = [d()];
    assert!(
        t.sample(
            Duration::ZERO,
            Some(DesktopCursor { x: -900., y: 100. }),
            &displays
        )
        .is_none()
    );
    let v = t
        .sample(
            Duration::from_millis(100),
            Some(DesktopCursor { x: -850., y: 100. }),
            &displays,
        )
        .unwrap();
    assert!((v.velocity_mm_s.x - 100.).abs() < 0.001);
    assert!((v.position_mm.x - 30.0).abs() < 0.0001);
    assert!(
        t.sample(Duration::from_millis(150), None, &displays)
            .is_none()
    );
    assert!(
        t.sample(
            Duration::from_millis(200),
            Some(DesktopCursor { x: -800., y: 100. }),
            &displays
        )
        .is_none()
    );
    assert!(
        t.sample(
            Duration::from_secs(100),
            Some(DesktopCursor { x: -700., y: 100. }),
            &displays
        )
        .is_none()
    );
}
#[test]
fn unknown_calibration_or_cursor_outside_displays_never_disturbs() {
    let mut d = d();
    d.calibration = DisplayCalibration::resolve(None, None).unwrap();
    let mut t = CursorTracker::default();
    for i in 0..10 {
        assert!(
            t.sample(
                Duration::from_millis(i * 50),
                Some(DesktopCursor {
                    x: -900. + i as f64,
                    y: 100.
                }),
                &[d.clone()]
            )
            .is_none()
        );
    }
}
