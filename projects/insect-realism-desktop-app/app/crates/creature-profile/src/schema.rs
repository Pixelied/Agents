use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

pub const SCHEMA_VERSION: u32 = 1;
pub const PROFILE_MAGIC: &[u8; 8] = b"ANTBIO01";

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct CreatureId(pub u64);
#[derive(Clone, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct CreatureKind(pub String);
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct RangeF32 {
    pub min: f32,
    pub max: f32,
}
impl RangeF32 {
    pub fn interpolate(self, u: f32) -> f32 {
        self.min + (self.max - self.min) * u
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Unit {
    Millimeters,
    MillimetersPerSecond,
    MillimetersPerSecondSquared,
    Seconds,
    Radians,
    RadiansPerSecond,
    Scalar,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum EvidenceBasis {
    Measurement,
    DerivedMeasurement,
    DonorTransfer,
    EngineeringAssumption,
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct EvidenceRef {
    pub source_id: String,
    pub source_file: String,
    pub source_field: String,
    pub source_sha256: String,
    pub context: String,
    pub basis: EvidenceBasis,
    pub note: String,
    pub review_state: String,
    pub license_class: String,
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Parameter {
    pub range: RangeF32,
    pub unit: Unit,
    pub evidence: Vec<EvidenceRef>,
}
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct MotionSample {
    pub dt_s: f32,
    pub speed_mm_s: f32,
    pub angular_velocity_rad_s: f32,
    pub acceleration_mm_s2: f32,
    pub original_frame: u32,
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct MotionTrack {
    pub track_id: String,
    pub species: String,
    pub condition: String,
    pub samples: Vec<MotionSample>,
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct CreatureProfile {
    pub id: CreatureKind,
    pub species: String,
    pub qualified: bool,
    pub parameters: BTreeMap<String, Parameter>,
    pub motion_tracks: Vec<MotionTrack>,
    pub motion_evidence: Vec<EvidenceRef>,
    pub limitations: Vec<String>,
}
impl CreatureProfile {
    pub fn parameter(&self, name: &str) -> Result<&Parameter, crate::ProfileError> {
        self.parameters
            .get(name)
            .ok_or_else(|| crate::ProfileError::MissingField(name.into()))
    }
    pub fn range(&self, name: &str) -> Result<RangeF32, crate::ProfileError> {
        Ok(self.parameter(name)?.range)
    }
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct RuntimeProfileBundle {
    pub schema_version: u32,
    pub profile_version: String,
    pub pack_sha256: String,
    pub creatures: Vec<CreatureProfile>,
    pub input_hashes: BTreeMap<String, String>,
    pub warnings: Vec<String>,
}
