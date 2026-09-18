use criterion::{BenchmarkId, Criterion, criterion_group, criterion_main};
use display_model::DisplayId;
use glam::Vec2;
use simulation::spatial::SpatialHash;
fn benchmarks(c: &mut Criterion) {
    let mut group = c.benchmark_group("spatial-mm");
    for n in [500, 1000, 2000] {
        let p: Vec<_> = (0..n)
            .map(|i| Vec2::new((i % 50) as f32 * 4., (i / 50) as f32 * 4.))
            .collect();
        let d = vec![DisplayId(1); n];
        let a: Vec<_> = (0..n).collect();
        let mut h = SpatialHash::new(n, 3.);
        group.bench_with_input(BenchmarkId::from_parameter(n), &n, |b, _| {
            b.iter(|| {
                h.rebuild(&p, &d, &a);
                for (i, &q) in p.iter().enumerate() {
                    std::hint::black_box(h.nearest(DisplayId(1), q, 3., i, 128));
                }
            })
        });
    }
    group.finish();
}
criterion_group!(benches, benchmarks);
criterion_main!(benches);
