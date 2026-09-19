use criterion::{BenchmarkId, Criterion, criterion_group, criterion_main};
use desktop_app::benchmark::{FRAME_DT, Scenario, fixture};
use std::{sync::Arc, time::Duration};
fn measurements(c: &mut Criterion) {
    let profiles = Arc::new(
        creature_profile::RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    );
    let mut group = c.benchmark_group("controller-nominal-population");
    group
        .sample_size(20)
        .warm_up_time(Duration::from_secs(1))
        .measurement_time(Duration::from_secs(3));
    for (name, scenario) in [
        ("realistic-50", Scenario::Realistic),
        ("heavy-500", Scenario::Heavy),
        ("stress-1200", Scenario::Stress1000),
        ("extreme-5000", Scenario::Extreme),
    ] {
        let mut controller = fixture(profiles.clone(), scenario).unwrap();
        for _ in 0..60 {
            controller.advance(FRAME_DT, None, true).unwrap();
        }
        group.bench_function(BenchmarkId::from_parameter(name), |b| {
            b.iter(|| {
                std::hint::black_box(
                    controller
                        .advance(Duration::from_nanos(33_333_334), None, true)
                        .unwrap(),
                );
                std::hint::black_box(controller.simulation().len());
            })
        });
    }
    group.finish();
}
criterion_group!(benches, measurements);
criterion_main!(benches);
