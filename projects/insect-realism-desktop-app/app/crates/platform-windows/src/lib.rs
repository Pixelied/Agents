mod policy;
pub use policy::*;
#[cfg(target_os = "windows")]
mod native;
#[cfg(target_os = "windows")]
pub use native::{WindowsAdapter, clear_startup_entry, enable_per_monitor_v2};
