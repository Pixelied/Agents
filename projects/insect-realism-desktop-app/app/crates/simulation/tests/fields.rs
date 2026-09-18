use display_model::*;
use glam::Vec2;
use proptest::prelude::*;
use simulation::{spatial::SpatialHash, trails::TrailField};
fn topology() -> DisplayTopology {
    DisplayTopology::new(
        vec![DisplaySurface {
            id: DisplayId(1),
            fingerprint: "f".into(),
            name: "f".into(),
            pixels: PixelSize {
                width: 400,
                height: 300,
            },
            desktop_bounds: DesktopRect {
                x: 0.,
                y: 0.,
                width: 400.,
                height: 300.,
            },
            scale_factor: 1.,
            refresh_hz: 60.,
            rotation_deg: 0,
            calibration: DisplayCalibration {
                mm_per_physical_px: 0.25,
                confidence: CalibrationConfidence::Manual,
            },
        }],
        false,
    )
    .unwrap()
}
#[test]
fn neighbors_include_self_and_do_not_leak_between_displays() {
    let mut grid = SpatialHash::new(4, 2.);
    grid.rebuild(
        &[Vec2::ZERO, Vec2::new(1., 0.), Vec2::ZERO],
        &[DisplayId(1), DisplayId(1), DisplayId(2)],
        &[0, 1, 2],
    );
    let mut out = Vec::new();
    grid.neighbors(DisplayId(1), Vec2::ZERO, 2., &mut out);
    out.sort();
    assert_eq!(out, vec![0, 1]);
}
proptest! {#![proptest_config(ProptestConfig::with_cases(64))]
#[test] fn grid_matches_brute_force(points in prop::collection::vec((-20f32..20.,-20f32..20.,0u64..3),1..80), x in -25f32..25., y in -25f32..25., r in 0.01f32..10., d in 0u64..3){
 let p:Vec<_>=points.iter().map(|a|Vec2::new(a.0,a.1)).collect();let ids:Vec<_>=points.iter().map(|a|DisplayId(a.2)).collect();let indices:Vec<_>=(0..p.len()).collect();let mut grid=SpatialHash::new(p.len(),2.);grid.rebuild(&p,&ids,&indices);let at=Vec2::new(x,y);let mut actual=Vec::new();grid.neighbors(DisplayId(d),at,r,&mut actual);actual.sort();let expected:Vec<_>=indices.into_iter().filter(|&i|ids[i]==DisplayId(d)&&p[i].distance_squared(at)<=r*r).collect();prop_assert_eq!(actual,expected);
}}
#[test]
fn trails_are_bounded_directional_and_decay() {
    let mut f = TrailField::new(&topology(), 4., 30.).unwrap();
    for _ in 0..1000 {
        assert!(f.deposit(DisplayId(1), Vec2::new(10., 10.), Vec2::X, 0.1));
    }
    let a = f.sample(DisplayId(1), Vec2::new(10., 10.));
    assert!(a.intensity > 0.);
    assert!(a.intensity <= 1.);
    assert!(a.direction.x > 0.9);
    f.decay_step(30.);
    let b = f.sample(DisplayId(1), Vec2::new(10., 10.));
    assert!(b.intensity < a.intensity);
    assert!((b.intensity / a.intensity - (-1f32).exp()).abs() < 0.001);
    assert_eq!(f.sample(DisplayId(2), Vec2::new(10., 10.)).intensity, 0.);
}
#[test]
fn invalid_trail_geometry_is_rejected() {
    assert!(TrailField::new(&topology(), 0., 30.).is_err());
    assert!(TrailField::new(&topology(), 4., f32::NAN).is_err());
}
