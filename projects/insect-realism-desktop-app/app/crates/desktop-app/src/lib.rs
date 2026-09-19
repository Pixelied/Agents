pub mod compatibility;
pub mod controller;
pub mod lifecycle;
pub mod overlay_host;
pub mod recovery;

pub mod cursor;

pub mod preferences;

#[cfg(feature = "native-ui")]
pub mod gui;

#[cfg(feature = "native-ui")]
pub mod app;
#[cfg(feature = "native-ui")]
mod settings_window;
#[cfg(feature = "native-ui")]
pub mod validation;

#[cfg(feature = "native-ui")]
pub mod benchmark;
pub mod metrics;

#[cfg(feature = "native-ui")]
pub mod soak;
