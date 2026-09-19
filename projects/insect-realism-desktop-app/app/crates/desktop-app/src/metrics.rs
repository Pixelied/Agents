//! Developer measurements. Nothing here changes the simulation or its biological parameters.
use serde::Serialize;
#[cfg(feature = "native-ui")]
use std::collections::BTreeMap;
use std::path::Path;

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Distribution {
    pub count: usize,
    pub min: f64,
    pub median: f64,
    pub p95: f64,
    pub p99: f64,
    pub worst: f64,
    pub mean: f64,
}
impl Distribution {
    /// Nearest-rank percentiles, with interpolation deliberately avoided for reproducibility.
    /// Missing samples remain missing; NaN/negative measurements are errors, not zeroes.
    pub fn new(values: &[f64]) -> Result<Option<Self>, &'static str> {
        if values.iter().any(|v| !v.is_finite() || *v < 0.) {
            return Err("measurements must be finite and nonnegative");
        }
        if values.is_empty() {
            return Ok(None);
        }
        let mut sorted = values.to_vec();
        sorted.sort_by(f64::total_cmp);
        let rank = |q: f64| sorted[((q * sorted.len() as f64).ceil() as usize).saturating_sub(1)];
        Ok(Some(Self {
            count: sorted.len(),
            min: sorted[0],
            median: rank(0.5),
            p95: rank(0.95),
            p99: rank(0.99),
            worst: *sorted.last().unwrap(),
            mean: sorted.iter().map(|x| x / sorted.len() as f64).sum(),
        }))
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct ProcessSample {
    /// Linux VmRSS from /proc, bytes. Other hosts remain None, never a fabricated zero.
    pub resident_bytes: Option<u64>,
    pub os_handle_count: Option<u64>,
    pub os_handle_kind: &'static str,
}
pub fn process_sample() -> ProcessSample {
    #[cfg(target_os = "linux")]
    {
        let resident_bytes = std::fs::read_to_string("/proc/self/status")
            .ok()
            .and_then(|s| {
                s.lines()
                    .find(|line| line.starts_with("VmRSS:"))
                    .and_then(|line| line.split_whitespace().nth(1)?.parse::<u64>().ok())
                    .map(|kb| kb * 1024)
            });
        let os_handle_count = std::fs::read_dir("/proc/self/fd")
            .ok()
            .map(|d| d.count().saturating_sub(1) as u64);
        ProcessSample {
            resident_bytes,
            os_handle_count,
            os_handle_kind: "Linux file descriptors",
        }
    }
    #[cfg(not(target_os = "linux"))]
    {
        ProcessSample {
            resident_bytes: None,
            os_handle_count: None,
            os_handle_kind: "not sampled on this host",
        }
    }
}

/// Registry inventory is live resource counts, NOT resident GPU memory or VRAM bytes.
#[cfg(feature = "native-ui")]
pub fn gpu_inventory(gpu: &rendering::Renderer) -> Option<BTreeMap<&'static str, usize>> {
    let report = gpu.instance().generate_report()?;
    let hub = report.hub;
    Some(BTreeMap::from([
        ("surfaces", report.surfaces.num_allocated),
        ("devices", hub.devices.num_allocated),
        ("queues", hub.queues.num_allocated),
        ("buffers", hub.buffers.num_allocated),
        ("textures", hub.textures.num_allocated),
        ("texture_views", hub.texture_views.num_allocated),
        ("bind_groups", hub.bind_groups.num_allocated),
        ("render_pipelines", hub.render_pipelines.num_allocated),
        ("query_sets", hub.query_sets.num_allocated),
        ("command_buffers", hub.command_buffers.num_allocated),
    ]))
}

pub fn write_json(path: &Path, value: &impl Serialize) -> Result<(), Box<dyn std::error::Error>> {
    use std::io::Write;
    let parent = path
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .unwrap_or(Path::new("."));
    std::fs::create_dir_all(parent)?;
    let mut temp = tempfile::NamedTempFile::new_in(parent)?;
    serde_json::to_writer_pretty(&mut temp, value)?;
    temp.write_all(b"\n")?;
    temp.as_file().sync_all()?;
    temp.persist(path)?;
    Ok(())
}

#[cfg(feature = "allocation-metrics")]
mod allocations {
    use std::{
        alloc::{GlobalAlloc, Layout, System},
        cell::Cell,
    };
    thread_local! {
        static ENABLED: Cell<bool> = const { Cell::new(false) };
        static COUNT: Cell<u64> = const { Cell::new(0) };
    }
    pub struct Counter;
    fn record() {
        if ENABLED.try_with(Cell::get).unwrap_or(false) {
            let _ = COUNT.try_with(|n| n.set(n.get().saturating_add(1)));
        }
    }
    // SAFETY: all allocations/deallocations forward their arguments unchanged to System.
    // Const-initialized TLS counters do not allocate, and TLS teardown is handled by try_with.
    unsafe impl GlobalAlloc for Counter {
        unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
            record();
            unsafe { System.alloc(layout) }
        }
        unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
            record();
            unsafe { System.alloc_zeroed(layout) }
        }
        unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, size: usize) -> *mut u8 {
            record();
            unsafe { System.realloc(ptr, layout, size) }
        }
        unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
            unsafe { System.dealloc(ptr, layout) }
        }
    }
    #[global_allocator]
    static ALLOCATOR: Counter = Counter;
    pub fn measure<T>(f: impl FnOnce() -> T) -> (T, Option<u64>) {
        assert!(
            !ENABLED.with(Cell::get),
            "nested allocation measurement is unsupported"
        );
        struct Reset;
        impl Drop for Reset {
            fn drop(&mut self) {
                ENABLED.with(|v| v.set(false));
            }
        }
        COUNT.with(|n| n.set(0));
        ENABLED.with(|n| n.set(true));
        let reset = Reset;
        let result = f();
        drop(reset);
        (result, Some(COUNT.with(Cell::get)))
    }
}
#[cfg(feature = "allocation-metrics")]
pub use allocations::measure as measure_allocations;
#[cfg(not(feature = "allocation-metrics"))]
pub fn measure_allocations<T>(f: impl FnOnce() -> T) -> (T, Option<u64>) {
    (f(), None)
}
