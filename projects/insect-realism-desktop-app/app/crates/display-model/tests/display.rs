use display_model::*;
use glam::Vec2;
use proptest::prelude::*;
fn screen(id: u64, x: f64, ppi: f32) -> DisplaySurface {
    let mm = 25.4 / ppi;
    DisplaySurface {
        id: DisplayId(id),
        fingerprint: format!("test-{id}"),
        name: format!("Screen {id}"),
        pixels: PixelSize {
            width: 1920,
            height: 1080,
        },
        desktop_bounds: DesktopRect {
            x,
            y: 0.0,
            width: 1920.0,
            height: 1080.0,
        },
        scale_factor: 1.0,
        refresh_hz: 60.0,
        rotation_deg: 0,
        calibration: DisplayCalibration {
            mm_per_physical_px: mm,
            confidence: CalibrationConfidence::Manual,
        },
    }
}
#[test]
fn physical_size_is_independent_of_ui_scale() {
    let a = screen(1, 0.0, 110.0);
    let mut b = screen(2, 1920.0, 220.0);
    b.scale_factor = 2.0;
    let pa = a.mm_to_physical_px(Vec2Mm::new(3.0, 0.0).unwrap()).x;
    let pb = b.mm_to_physical_px(Vec2Mm::new(3.0, 0.0).unwrap()).x;
    assert!((pb - 2.0 * pa).abs() < 1e-5);
    assert!((pb * b.calibration.mm_per_physical_px - 3.0).abs() < 1e-5);
}
#[test]
fn credit_card_calibration_uses_physical_pixels() {
    let c = DisplayCalibration::from_card_width(600.0).unwrap();
    assert!((c.mm_per_physical_px - 85.60 / 600.0).abs() < 1e-6);
    assert_eq!(c.confidence, CalibrationConfidence::Manual);
    assert!(DisplayCalibration::from_card_width(0.0).is_err());
}
#[test]
fn unknown_metadata_is_never_inferred_from_retina_scale() {
    let c = DisplayCalibration::resolve(None, None).unwrap();
    assert_eq!(c.confidence, CalibrationConfidence::NeedsManual);
    assert!(!c.is_ready());
}
#[test]
fn saved_manual_calibration_remains_authoritative() {
    let c = DisplayCalibration::resolve(Some(0.12), Some(0.25)).unwrap();
    assert_eq!(c.confidence, CalibrationConfidence::Manual);
    assert_eq!(c.mm_per_physical_px, 0.12);
}
#[test]
fn crossing_preserves_millimeter_velocity_in_mixed_density_layout() {
    let a = screen(1, -1920.0, 220.0);
    let b = screen(2, 0.0, 110.0);
    let topology = DisplayTopology::new(vec![a.clone(), b], true).unwrap();
    let v = Vec2::new(15.0, 2.0);
    let c = topology
        .crossing(a.id, Vec2::new(a.size_mm().x + 0.2, 30.0), v)
        .unwrap();
    assert_eq!(c.display, DisplayId(2));
    assert!((c.position_mm - Vec2::new(0.2, 30.0)).length() < 1e-4);
    assert_eq!(c.velocity_mm_s, v);
}
#[test]
fn independent_mode_and_real_gaps_do_not_connect() {
    let a = screen(1, 0.0, 110.0);
    let b = screen(2, 1920.0, 110.0);
    let pos = Vec2::new(a.size_mm().x + 0.2, 20.0);
    assert!(
        DisplayTopology::new(vec![a.clone(), b], false)
            .unwrap()
            .crossing(a.id, pos, Vec2::X)
            .is_none()
    );
    let gap = screen(2, 1930.0, 110.0);
    assert!(
        DisplayTopology::new(vec![a.clone(), gap], true)
            .unwrap()
            .crossing(a.id, pos, Vec2::X)
            .is_none()
    );
}
#[test]
fn duplicate_displays_and_nonfinite_bounds_are_rejected() {
    let a = screen(1, 0.0, 110.0);
    assert!(DisplayTopology::new(vec![a.clone(), a.clone()], true).is_err());
    let mut bad = a;
    bad.desktop_bounds.x = f64::NAN;
    assert!(DisplayTopology::new(vec![bad], true).is_err());
}
proptest! {
 #[test]
 fn sane_displays_produce_finite_transforms(ppi in 50.0f32..600.0, scale in 0.75f64..4.0, x in -10000i32..10000) {
    let mut d=screen(1,x as f64,ppi); d.scale_factor=scale;
    d.validate().unwrap();
    let px=d.mm_to_physical_px(Vec2Mm::new(3.0,4.0).unwrap());
    prop_assert!(px.is_finite());
    let topology=DisplayTopology::new(vec![d],true).unwrap();
    prop_assert_eq!(topology.connection_count(),0);
 }
}
