//! Small, local, versioned configuration. No network, accounts, or telemetry.
mod config;
mod migrate;
mod preset;
pub use config::*;
pub use preset::*;
#[cfg(feature = "ui")]
pub mod calibration_ui;
#[cfg(feature = "ui")]
pub mod ui;
