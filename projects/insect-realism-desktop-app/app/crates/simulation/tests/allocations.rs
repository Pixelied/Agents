use creature_profile::RuntimeProfileBundle;
use display_model::*;
use simulation::*;
use std::{
    alloc::{GlobalAlloc, Layout, System},
    cell::Cell,
    sync::Arc,
    time::Duration,
};
struct Tracking;
thread_local! {static RECORD:Cell<bool>=const{Cell::new(false)};static ALLOCS:Cell<u64>=const{Cell::new(0)};}
fn record() {
    if RECORD.try_with(Cell::get).unwrap_or(false) {
        let _ = ALLOCS.try_with(|n| n.set(n.get() + 1));
    }
}
// SAFETY: every allocation is forwarded unchanged to the system allocator.
unsafe impl GlobalAlloc for Tracking {
    unsafe fn alloc(&self, l: Layout) -> *mut u8 {
        record();
        unsafe { System.alloc(l) }
    }
    unsafe fn dealloc(&self, p: *mut u8, l: Layout) {
        unsafe { System.dealloc(p, l) }
    }
    unsafe fn realloc(&self, p: *mut u8, l: Layout, n: usize) -> *mut u8 {
        record();
        unsafe { System.realloc(p, l, n) }
    }
    unsafe fn alloc_zeroed(&self, l: Layout) -> *mut u8 {
        record();
        unsafe { System.alloc_zeroed(l) }
    }
}
#[global_allocator]
static ALLOCATOR: Tracking = Tracking;
#[test]
fn warmed_thousand_ant_ticks_allocate_nothing() {
    let profiles = Arc::new(
        RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    );
    let t = DisplayTopology::new(
        vec![DisplaySurface {
            id: DisplayId(1),
            fingerprint: "test".into(),
            name: "test".into(),
            pixels: PixelSize {
                width: 2400,
                height: 1600,
            },
            desktop_bounds: DesktopRect {
                x: 0.,
                y: 0.,
                width: 2400.,
                height: 1600.,
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
    .unwrap();
    let mut s = Simulation::new(
        SimulationConfig {
            capacity: 1200,
            ..SimulationConfig::default()
        },
        profiles,
    )
    .unwrap();
    s.set_target_population(1000).unwrap();
    for i in 0..1000 {
        s.spawn_developer(
            DisplayId(1),
            Vec2Mm {
                x: 50. + (i % 40) as f32 * 5.,
                y: 50. + (i / 40) as f32 * 5.,
            },
            i as f32,
        )
        .unwrap();
    }
    let e = EnvironmentSnapshot::new(&t);
    for _ in 0..5 {
        s.advance(Duration::from_millis(34), &e).unwrap();
    }
    ALLOCS.with(|n| n.set(0));
    RECORD.with(|v| v.set(true));
    for _ in 0..120 {
        let r = s.advance(Duration::from_millis(34), &e);
        assert!(r.is_ok());
        std::hint::black_box(s.visual_states());
    }
    RECORD.with(|v| v.set(false));
    let allocations = ALLOCS.with(Cell::get);
    assert_eq!(allocations, 0, "warmed steady-state heap allocations");
}
