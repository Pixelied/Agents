use creature_profile::RuntimeProfileBundle;
use display_model::*;
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
fn topology() -> DisplayTopology {
    DisplayTopology::new(
        vec![DisplaySurface {
            id: DisplayId(1),
            fingerprint: "fixture".into(),
            name: "fixture".into(),
            pixels: PixelSize {
                width: 1920,
                height: 1080,
            },
            desktop_bounds: DesktopRect {
                x: 0.,
                y: 0.,
                width: 1920.,
                height: 1080.,
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
fn fixed_step_advances_and_seeded_runs_repeat() {
    let p = profiles();
    let t = topology();
    let e = EnvironmentSnapshot {
        topology: &t,
        cursor: None,
    };
    let mut a = Simulation::new(SimulationConfig::default(), p.clone()).unwrap();
    let mut b = Simulation::new(SimulationConfig::default(), p).unwrap();
    a.set_target_population(10).unwrap();
    b.set_target_population(10).unwrap();
    let initial = a.signature();
    for _ in 0..300 {
        a.advance(Duration::from_millis(20), &e).unwrap();
        b.advance(Duration::from_millis(20), &e).unwrap();
    }
    assert!(!a.is_empty());
    assert_ne!(a.signature(), initial);
    assert_eq!(a.signature(), b.signature());
}
#[test]
fn paused_time_does_not_advance() {
    let p = profiles();
    let t = topology();
    let e = EnvironmentSnapshot {
        topology: &t,
        cursor: None,
    };
    let mut s = Simulation::new(SimulationConfig::default(), p).unwrap();
    s.set_paused(true);
    let h = s.signature();
    let r = s.advance(Duration::from_secs(3600), &e).unwrap();
    assert_eq!(r.ticks, 0);
    assert_eq!(s.signature(), h);
}
#[test]
fn stalls_have_a_bounded_catchup_budget() {
    let t = topology();
    let mut s = Simulation::new(SimulationConfig::default(), profiles()).unwrap();
    let r = s
        .advance(
            Duration::from_secs(3600),
            &EnvironmentSnapshot {
                topology: &t,
                cursor: None,
            },
        )
        .unwrap();
    assert!(r.ticks <= 8);
    assert!(r.dropped_ticks > 1000);
    assert!((0.0..1.0).contains(&r.alpha));
}
#[test]
fn invalid_capacity_and_population_are_rejected() {
    assert!(
        Simulation::new(
            SimulationConfig {
                capacity: 0,
                ..SimulationConfig::default()
            },
            profiles()
        )
        .is_err()
    );
    let mut s = Simulation::new(
        SimulationConfig {
            capacity: 20,
            ..SimulationConfig::default()
        },
        profiles(),
    )
    .unwrap();
    assert!(s.set_target_population(21).is_err());
}

#[test]
fn developer_batch_matches_individual_spawns_without_rebuilding_every_instance() {
    let p = profiles();
    let mut a = Simulation::new(SimulationConfig::default(), p.clone()).unwrap();
    let mut b = Simulation::new(SimulationConfig::default(), p).unwrap();
    let spawns = (0..100)
        .map(|i| {
            (
                DisplayId(1),
                Vec2Mm::new(i as f32 + 5., 20.).unwrap(),
                i as f32 / 10.,
            )
        })
        .collect::<Vec<_>>();
    a.spawn_developer_batch(&spawns).unwrap();
    for (d, p, h) in spawns {
        b.spawn_developer(d, p, h).unwrap();
    }
    assert_eq!(a.len(), 100);
    assert_eq!(a.snapshot(), b.snapshot());
}
