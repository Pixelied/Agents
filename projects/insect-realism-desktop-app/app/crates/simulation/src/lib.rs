//! Platform-independent, fixed-step simulation in physical millimeters.
//! All biological values originate in the compiled profile. See MODEL_ASSUMPTIONS.md
//! for deliberately non-biological scheduling/numerical policies.
mod clock;
mod snapshot;
mod state;
pub use clock::*;
pub use snapshot::*;
pub use state::*;
pub mod spatial;
pub mod trails;

mod behavior;
mod locomotion;
mod spawn;
