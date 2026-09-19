use desktop_app::metrics::{Distribution, measure_allocations};
#[test]
fn nearest_rank_percentiles_and_missing_samples_are_explicit() {
    let values: Vec<_> = (1..=100).map(f64::from).collect();
    let d = Distribution::new(&values).unwrap().unwrap();
    assert_eq!((d.median, d.p95, d.p99, d.worst), (50., 95., 99., 100.));
    assert_eq!(d.mean, 50.5);
    assert!(Distribution::new(&[]).unwrap().is_none());
    assert!(Distribution::new(&[f64::NAN]).is_err());
    assert!(Distribution::new(&[-0.1]).is_err());
    assert_eq!(Distribution::new(&[4.]).unwrap().unwrap().p99, 4.);
}
#[test]
fn allocation_counter_measures_real_allocations_or_reports_unavailable() {
    let (data, count) = measure_allocations(|| std::hint::black_box(vec![7u8; 4096]));
    assert_eq!(data.len(), 4096);
    #[cfg(feature = "allocation-metrics")]
    assert!(count.unwrap() >= 1);
    #[cfg(not(feature = "allocation-metrics"))]
    assert!(count.is_none());
    let (_, empty) = measure_allocations(|| std::hint::black_box(3u32 + 4));
    #[cfg(feature = "allocation-metrics")]
    assert_eq!(empty, Some(0));
    #[cfg(not(feature = "allocation-metrics"))]
    assert!(empty.is_none());
}
