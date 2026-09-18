use creature_profile::RuntimeProfileBundle;
use display_model::*;
use glam::Vec2;
use simulation::*;
use std::{sync::Arc, time::Duration};
fn profiles() -> Arc<RuntimeProfileBundle> {
    Arc::new(
        RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    )
}
fn display(id: u64, x: f64, mm: f32) -> DisplaySurface {
    DisplaySurface {
        id: DisplayId(id),
        fingerprint: format!("f{id}"),
        name: "fixture".into(),
        pixels: PixelSize {
            width: 400,
            height: 300,
        },
        desktop_bounds: DesktopRect {
            x,
            y: 0.,
            width: 400.,
            height: 300.,
        },
        scale_factor: 1.,
        refresh_hz: 60.,
        rotation_deg: 0,
        calibration: DisplayCalibration {
            mm_per_physical_px: mm,
            confidence: CalibrationConfidence::Manual,
        },
    }
}
fn topology(continuous: bool) -> DisplayTopology {
    DisplayTopology::new(
        vec![display(1, 0., 0.25), display(2, 400., 0.5)],
        continuous,
    )
    .unwrap()
}
fn moving_profile() -> Arc<RuntimeProfileBundle> {
    let mut p = (*profiles()).clone();
    for t in &mut p.creatures[0].motion_tracks {
        for s in &mut t.samples {
            s.speed_mm_s = 2.5430388;
            s.angular_velocity_rad_s = 0.;
            s.acceleration_mm_s2 = 0.;
        }
    }
    Arc::new(p)
}
#[test]
fn antenna_motion_is_persistent_and_asymmetric() {
    let t = topology(false);
    let mut s = Simulation::new(SimulationConfig::default(), moving_profile()).unwrap();
    s.spawn_developer(DisplayId(1), Vec2Mm { x: 40., y: 30. }, 0.)
        .unwrap();
    s.set_target_population(1).unwrap();
    let before = s.visual_states()[0].antenna;
    for _ in 0..60 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
    }
    let a = s.visual_states()[0].antenna;
    assert_ne!(before, a);
    assert!((a[0] - a[1]).abs() > 0.0001);
}
#[test]
fn continuous_crossing_preserves_mm_velocity_and_identity() {
    let t = topology(true);
    let mut s = Simulation::new(SimulationConfig::default(), moving_profile()).unwrap();
    let id = s
        .spawn_developer(DisplayId(1), Vec2Mm { x: 99., y: 25. }, 0.)
        .unwrap();
    s.set_target_population(1).unwrap();
    let mut crossed = false;
    for _ in 0..90 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
        for v in s.visual_states() {
            if v.id == id && v.display == DisplayId(2) {
                assert!(v.heading_rad.abs() < 0.01);
                assert!(v.speed_mm_s > 0.);
                assert!(v.position_mm.x < 40.);
                assert!(
                    v.previous_position_mm
                        .as_vec2()
                        .distance(v.position_mm.as_vec2())
                        < 3.
                );
                crossed = true;
            }
        }
        if crossed {
            break;
        }
    }
    assert!(crossed);
}
#[test]
fn independent_monitors_never_cross() {
    let t = topology(false);
    let mut s = Simulation::new(SimulationConfig::default(), moving_profile()).unwrap();
    let id = s
        .spawn_developer(DisplayId(1), Vec2Mm { x: 99., y: 25. }, 0.)
        .unwrap();
    s.set_target_population(1).unwrap();
    for _ in 0..90 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
        assert!(
            s.visual_states()
                .iter()
                .filter(|v| v.id == id)
                .all(|v| v.display == DisplayId(1))
        );
    }
}
#[test]
fn reducing_population_exits_without_leaving_unbounded_invisible_ants() {
    let t = topology(false);
    let mut s = Simulation::new(SimulationConfig::default(), moving_profile()).unwrap();
    s.spawn_developer(DisplayId(1), Vec2Mm { x: 98., y: 25. }, 0.)
        .unwrap();
    s.set_target_population(0).unwrap();
    for _ in 0..500 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
    }
    assert!(s.is_empty());
}
#[test]
fn cursor_disturbance_requires_explicit_opt_in() {
    let t = topology(false);
    let cursor = CursorDisturbance {
        display: DisplayId(1),
        position_mm: Vec2Mm { x: 49., y: 30. },
        velocity_mm_s: Vec2::new(200., 0.),
        strength: 1.,
    };
    let e = EnvironmentSnapshot {
        topology: &t,
        cursor: Some(cursor),
    };
    for enabled in [false, true] {
        let mut s = Simulation::new(SimulationConfig::default(), moving_profile()).unwrap();
        s.spawn_developer(DisplayId(1), Vec2Mm { x: 50., y: 30. }, 0.)
            .unwrap();
        s.set_target_population(1).unwrap();
        s.configure_population(3., 1., 0., 1., enabled).unwrap();
        s.advance(Duration::from_millis(34), &e).unwrap();
        assert_eq!(
            s.visual_states()[0].behavior == BehaviorState::Disturbance,
            enabled
        );
    }
}
#[test]
fn no_qualified_grooming_is_invented() {
    let t = topology(false);
    let mut s = Simulation::new(SimulationConfig::default(), profiles()).unwrap();
    s.set_target_population(30).unwrap();
    for _ in 0..1000 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
        assert!(
            s.visual_states()
                .iter()
                .all(|v| v.behavior != BehaviorState::Groom)
        );
    }
}
#[test]
fn stopped_ants_stop_their_gait() {
    let mut p = (*profiles()).clone();
    for t in &mut p.creatures[0].motion_tracks {
        for m in &mut t.samples {
            m.speed_mm_s = 0.;
            m.angular_velocity_rad_s = 0.;
        }
    }
    let t = topology(false);
    let mut s = Simulation::new(SimulationConfig::default(), Arc::new(p)).unwrap();
    s.spawn_developer(DisplayId(1), Vec2Mm { x: 50., y: 30. }, 0.)
        .unwrap();
    s.set_target_population(1).unwrap();
    for _ in 0..100 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
    }
    let v = &s.visual_states()[0];
    assert_eq!(v.speed_mm_s, 0.);
    assert_eq!(v.gait_advance, 0.);
}
#[test]
fn normal_spawns_come_from_edges() {
    let t = topology(false);
    let mut s = Simulation::new(SimulationConfig::default(), moving_profile()).unwrap();
    s.set_target_population(1).unwrap();
    for _ in 0..30 {
        s.advance(Duration::from_millis(34), &EnvironmentSnapshot::new(&t))
            .unwrap();
        if let Some(v) = s.visual_states().first() {
            let size = t.display(v.display).unwrap().size_mm();
            let p = v.previous_position_mm;
            let distance = p.x.min(p.y).min(size.x - p.x).min(size.y - p.y);
            assert!(distance < 1.);
            return;
        }
    }
    panic!("no edge entry occurred");
}
#[test]
fn render_partition_does_not_change_biology() {
    let t = topology(false);
    let mut hashes = Vec::new();
    for fps in [60u64, 120, 144, 165, 240] {
        let mut s = Simulation::new(
            SimulationConfig {
                seed: 54,
                ..SimulationConfig::default()
            },
            profiles(),
        )
        .unwrap();
        s.set_target_population(20).unwrap();
        let mut last = 0;
        for i in 1..=fps * 3 {
            let now = i * 1_000_000_000 / fps;
            s.advance(
                Duration::from_nanos(now - last),
                &EnvironmentSnapshot::new(&t),
            )
            .unwrap();
            last = now;
        }
        hashes.push(s.signature());
    }
    assert!(hashes.iter().all(|h| h == &hashes[0]));
}
