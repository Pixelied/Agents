//! Allocation regression for instance preparation, not GPU driver allocations.
use creature_profile::{CreatureId, RuntimeProfileBundle};
use display_model::*;
use rendering::InstanceBuilder;
use simulation::{Simulation, SimulationConfig};
use std::{
    alloc::{GlobalAlloc, Layout, System},
    cell::Cell,
    sync::Arc,
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
fn warmed_instance_preparation_with_resident_id_churn_allocates_nothing() {
    let profiles = Arc::new(
        RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    );
    let mut sim = Simulation::new(
        SimulationConfig {
            capacity: 1,
            ..Default::default()
        },
        profiles.clone(),
    )
    .unwrap();
    sim.spawn_developer(DisplayId(1), Vec2Mm::new(10., 10.).unwrap(), 0.)
        .unwrap();
    let base = sim.visual_states()[0];
    let mut states: Vec<_> = (0..1000)
        .map(|i| {
            let mut value = base;
            value.id = CreatureId(i + 1);
            value.position_mm =
                Vec2Mm::new(10. + (i % 40) as f32 * 4., 10. + (i / 40) as f32 * 4.).unwrap();
            value.previous_position_mm = value.position_mm;
            value
        })
        .collect();
    let display = DisplaySurface {
        id: DisplayId(1),
        fingerprint: "allocation-test".into(),
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
    };
    let mut builder = InstanceBuilder::new(1000, &profiles.creatures[0]).unwrap();
    for _ in 0..4 {
        builder.build(&states, &display, 0.5, 1.).unwrap();
    }
    // Prove that this test's counter observes a real heap allocation.
    ALLOCS.with(|n| n.set(0));
    RECORD.with(|v| v.set(true));
    let control = std::hint::black_box(vec![7u8; 4096]);
    RECORD.with(|v| v.set(false));
    assert!(ALLOCS.with(Cell::get) > 0);
    drop(control);
    ALLOCS.with(|n| n.set(0));
    RECORD.with(|v| v.set(true));
    for frame in 0..160 {
        // Churn a quarter of the active IDs to exercise bounded history replacement.
        for state in states.iter_mut().take(250) {
            state.id.0 += 1000;
        }
        let result = builder.build(&states, &display, (frame % 4) as f32 * 0.25, 1.);
        assert_eq!(result.unwrap().len(), 1000);
    }
    RECORD.with(|v| v.set(false));
    assert_eq!(
        ALLOCS.with(Cell::get),
        0,
        "instance preparation must retain preallocated storage"
    );
}
