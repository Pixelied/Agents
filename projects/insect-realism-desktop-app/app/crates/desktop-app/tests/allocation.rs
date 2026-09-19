#![cfg(feature = "allocation-metrics")]
use desktop_app::metrics::measure_allocations;
#[test]
fn counter_detects_real_allocations_not_a_constant_zero() {
    let (value, calls) = measure_allocations(|| {
        let v = vec![19u8; 4096];
        std::hint::black_box(v.as_ptr());
        v
    });
    assert_eq!(value.len(), 4096);
    assert!(calls.unwrap() >= 1);
}
#[test]
fn clean_measurement_does_not_inherit_prior_allocations() {
    let (_, calls) = measure_allocations(|| {
        std::hint::black_box(vec![1u8; 4000]);
    });
    assert!(calls.unwrap() >= 1);
    let (result, calls) = measure_allocations(|| std::hint::black_box(7));
    assert_eq!((result, calls), (7, Some(0)));
}
