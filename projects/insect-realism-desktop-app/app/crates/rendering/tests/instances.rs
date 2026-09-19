use creature_profile::RuntimeProfileBundle;
use display_model::*;
use rendering::*;
use simulation::*;
use std::sync::Arc;
fn display(mm: f32) -> DisplaySurface {
    DisplaySurface {
        id: DisplayId(1),
        fingerprint: "fixture".into(),
        name: "fixture".into(),
        pixels: PixelSize {
            width: 1000,
            height: 800,
        },
        desktop_bounds: DesktopRect {
            x: 0.,
            y: 0.,
            width: 1000.,
            height: 800.,
        },
        scale_factor: 2.,
        refresh_hz: 144.,
        rotation_deg: 0,
        calibration: DisplayCalibration {
            mm_per_physical_px: mm,
            confidence: CalibrationConfidence::Manual,
        },
    }
}
fn data() -> (RuntimeProfileBundle, VisualCreatureState) {
    let p = RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))
    .unwrap();
    let mut s = Simulation::new(SimulationConfig::default(), Arc::new(p.clone())).unwrap();
    s.spawn_developer(DisplayId(1), Vec2Mm { x: 20., y: 25. }, 0.)
        .unwrap();
    (p, s.visual_states()[0])
}
#[test]
fn instance_is_compact_pod() {
    assert_eq!(std::mem::size_of::<CreatureRenderInstance>(), 96);
    let v = CreatureRenderInstance::default();
    assert_eq!(bytemuck::bytes_of(&v).len(), 96);
}
#[test]
fn interpolation_uses_physical_mm_not_ui_scale() {
    let (p, mut v) = data();
    v.previous_position_mm = Vec2Mm { x: 10., y: 10. };
    v.position_mm = Vec2Mm { x: 20., y: 30. };
    v.body_length_mm = 3.;
    let mut b = InstanceBuilder::new(100, &p.creatures[0]).unwrap();
    let f = b.build(&[v], &display(0.1), 0.5, 1.).unwrap();
    assert_eq!(f.len(), 1);
    assert_eq!(f[0].position_px, [150., 200.]);
    assert_eq!(f[0].length_px, 30.);
}
#[test]
fn invalid_data_cannot_enter_gpu_buffers() {
    let (p, mut v) = data();
    v.position_mm.x = f32::NAN;
    let mut b = InstanceBuilder::new(1, &p.creatures[0]).unwrap();
    assert!(b.build(&[v], &display(0.2), 0.5, 1.).is_err());
}
#[test]
fn needs_manual_calibration_produces_no_ants() {
    let (p, v) = data();
    let mut d = display(0.2);
    d.calibration.confidence = CalibrationConfidence::NeedsManual;
    let mut b = InstanceBuilder::new(1, &p.creatures[0]).unwrap();
    assert!(b.build(&[v], &d, 0.5, 1.).unwrap().is_empty());
}
#[test]
fn lod_has_hysteresis() {
    assert_eq!(select_lod(13., Some(Lod::Tiny)), Lod::Tiny);
    assert_eq!(select_lod(11., Some(Lod::Standard)), Lod::Standard);
    assert_eq!(select_lod(15., Some(Lod::Tiny)), Lod::Standard);
    assert_eq!(select_lod(40., Some(Lod::Standard)), Lod::Detailed);
    assert_eq!(select_lod(9., Some(Lod::Standard)), Lod::Tiny);
}
#[test]
fn heading_interpolates_the_short_arc() {
    let (p, mut v) = data();
    v.previous_heading_rad = 3.1;
    v.heading_rad = -3.1;
    let mut b = InstanceBuilder::new(1, &p.creatures[0]).unwrap();
    let f = b.build(&[v], &display(0.2), 0.5, 1.).unwrap();
    assert_eq!(f.len(), 1);
    assert!(f[0].heading_rad.abs() > 3.);
}

#[test]
fn distinct_creature_ids_do_not_evict_each_others_lod_history() {
    let (profile, mut first) = data();
    first.id = creature_profile::CreatureId(1);
    first.body_length_mm = 3.;
    let mut second = first;
    // These IDs collide in the old direct-mapped four-slot cache despite only two ants.
    second.id = creature_profile::CreatureId(5);
    second.position_mm.x += 5.;
    second.previous_position_mm = second.position_mm;
    let mut builder = InstanceBuilder::new(2, &profile.creatures[0]).unwrap();
    let screen = display(0.3);
    let initial = builder.build(&[first, second], &screen, 1., 1.).unwrap();
    assert!(
        initial
            .iter()
            .all(|instance| instance.lod == Lod::Tiny as u32)
    );
    // A deliberate user scale change remains inside the existing tiny-LOD hysteresis.
    for _ in 0..4 {
        let instances = builder.build(&[first, second], &screen, 1., 1.25).unwrap();
        assert!(
            instances
                .iter()
                .all(|instance| instance.lod == Lod::Tiny as u32),
            "another resident must not erase an ant's LOD history"
        );
    }
}

#[test]
fn absent_creatures_do_not_leave_unbounded_or_stale_lod_history() {
    let (profile, mut creature) = data();
    creature.body_length_mm = 3.;
    let mut builder = InstanceBuilder::new(1, &profile.creatures[0]).unwrap();
    let screen = display(0.3);
    builder.build(&[creature], &screen, 1., 1.).unwrap();
    builder.build(&[], &screen, 1., 1.).unwrap();
    let instances = builder.build(&[creature], &screen, 1., 1.25).unwrap();
    assert_eq!(
        instances[0].lod,
        Lod::Standard as u32,
        "a reintroduced diagnostic ID starts without a disappeared resident's history"
    );
}

#[test]
fn instance_builder_rejects_population_beyond_reserved_capacity() {
    let (profile, first) = data();
    let mut second = first;
    second.id = creature_profile::CreatureId(first.id.0 + 1);
    let mut builder = InstanceBuilder::new(1, &profile.creatures[0]).unwrap();
    assert!(
        builder
            .build(&[first, second], &display(0.3), 1., 1.)
            .is_err(),
        "capacity must be reserved outside the frame loop, never silently grown"
    );
}
