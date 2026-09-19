//! GPU-instanced physical-size ant renderer, independent from native window policy.
mod instance;
mod lod;
pub use instance::*;
pub use lod::*;
#[derive(Debug, thiserror::Error)]
pub enum RenderError {
    #[error("invalid rendering input: {0}")]
    Invalid(&'static str),
    #[error("graphics backend: {0}")]
    Backend(String),
    #[error("transparent compositor surface is unavailable")]
    NoTransparency,
    #[error("graphics device lost: {0}")]
    DeviceLost(String),
}

mod renderer;
pub use renderer::*;

mod validation;
pub use validation::*;

mod surface;
pub use surface::*;
