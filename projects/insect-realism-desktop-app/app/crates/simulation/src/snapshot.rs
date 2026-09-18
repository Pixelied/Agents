use crate::{Simulation, VisualCreatureState};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
/// Snapshots allocate intentionally, outside the realtime tick path.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct SimulationSnapshot {
    pub seed: u64,
    pub tick: u64,
    pub profile_version: String,
    pub states: Vec<VisualCreatureState>,
}
impl SimulationSnapshot {
    pub(crate) fn capture(s: &Simulation) -> Self {
        let mut states = s.visual_states().to_vec();
        states.sort_by_key(|s| s.id.0);
        Self {
            seed: s.seed,
            tick: s.tick_count(),
            profile_version: s.profiles().profile_version.clone(),
            states,
        }
    }
    pub fn signature(&self) -> String {
        let bytes =
            postcard::to_allocvec(self).expect("snapshot contains serializable finite fields");
        format!("{:x}", Sha256::digest(bytes))
    }
}
