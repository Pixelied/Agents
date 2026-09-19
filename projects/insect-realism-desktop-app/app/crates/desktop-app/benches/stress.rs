use criterion::{BenchmarkId, Criterion, criterion_group, criterion_main};
use desktop_app::benchmark::{FRAME_DT, Harness, Scenario};
use std::{sync::Arc, time::Duration};
fn measurements(c: &mut Criterion) {
    let profiles = Arc::new(
        creature_profile::RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    );
    let mut group = c.benchmark_group("completed-offscreen-GPU-frame");
    group
        .sample_size(10)
        .warm_up_time(Duration::from_secs(1))
        .measurement_time(Duration::from_secs(3));
    for (name, scenario) in [
        ("realistic", Scenario::Realistic),
        ("heavy", Scenario::Heavy),
        ("stress1000", Scenario::Stress1000),
        ("extreme", Scenario::Extreme),
    ] {
        let mut h = Harness::new(profiles.clone(), scenario).unwrap();
        for i in 0..60 {
            h.frame(i, true, FRAME_DT).unwrap();
        }
        group.bench_function(BenchmarkId::from_parameter(name), |b| {
            b.iter(|| {
                std::hint::black_box(h.frame(0, true, FRAME_DT).unwrap());
            })
        });
    }
    group.finish();
}
criterion_group!(benches, measurements);
criterion_main!(benches);
