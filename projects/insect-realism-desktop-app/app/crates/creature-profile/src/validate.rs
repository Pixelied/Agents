use crate::*;
use sha2::{Digest, Sha256};
use std::collections::HashSet;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ProfileError {
    #[error("invalid physical range: {0}")]
    InvalidRange(String),
    #[error("unsupported profile schema {0}")]
    UnsupportedSchema(u32),
    #[error("missing profile field/evidence: {0}")]
    MissingField(String),
    #[error("invalid profile: {0}")]
    Invalid(String),
    #[error("profile digest mismatch or invalid envelope")]
    Digest,
    #[error("profile codec: {0}")]
    Codec(#[from] postcard::Error),
}
impl RangeF32 {
    pub fn validate(self, signed: bool) -> Result<(), ProfileError> {
        if !self.min.is_finite()
            || !self.max.is_finite()
            || self.min > self.max
            || (!signed && self.min < 0.0)
        {
            Err(ProfileError::InvalidRange(format!(
                "{}..{}",
                self.min, self.max
            )))
        } else {
            Ok(())
        }
    }
}
fn validate_evidence(refs: &[EvidenceRef], name: &str) -> Result<(), ProfileError> {
    if refs.is_empty() {
        return Err(ProfileError::MissingField(format!("{name}: evidence")));
    }
    for r in refs {
        if r.source_id.is_empty()
            || r.context.is_empty()
            || r.note.is_empty()
            || r.source_field.is_empty()
        {
            return Err(ProfileError::MissingField(format!(
                "{name}: incomplete provenance"
            )));
        }
        if r.basis != EvidenceBasis::EngineeringAssumption
            && (r.source_file.is_empty()
                || r.source_sha256.len() != 64
                || !r.source_sha256.bytes().all(|b| b.is_ascii_hexdigit()))
        {
            return Err(ProfileError::Invalid(format!("{name}: untraceable source")));
        }
    }
    Ok(())
}
impl RuntimeProfileBundle {
    pub fn validate(&self) -> Result<(), ProfileError> {
        if self.schema_version != SCHEMA_VERSION {
            return Err(ProfileError::UnsupportedSchema(self.schema_version));
        }
        if self.profile_version.is_empty() {
            return Err(ProfileError::MissingField("profile_version".into()));
        }
        let mut ids = HashSet::new();
        let mut ant = false;
        for c in &self.creatures {
            if !ids.insert(&c.id.0) {
                return Err(ProfileError::Invalid("duplicate creature id".into()));
            }
            if c.id.0 == "ant" {
                ant = true;
            }
            if c.species.is_empty() {
                return Err(ProfileError::MissingField("species".into()));
            }
            for (name, p) in &c.parameters {
                p.range.validate(matches!(
                    p.unit,
                    Unit::MillimetersPerSecondSquared | Unit::RadiansPerSecond | Unit::Radians
                ))?;
                validate_evidence(&p.evidence, name)?;
            }
            if c.id.0 == "ant" {
                for (name, unit) in [
                    ("body_length_mm", Unit::Millimeters),
                    ("head_length_mm", Unit::Millimeters),
                    ("head_width_mm", Unit::Millimeters),
                    ("stride_length_mm", Unit::Millimeters),
                    ("preferred_speed_mm_s", Unit::MillimetersPerSecond),
                    ("climb_up_speed_mm_s", Unit::MillimetersPerSecond),
                    ("climb_down_speed_mm_s", Unit::MillimetersPerSecond),
                    ("pause_duration_s", Unit::Seconds),
                    ("direction_persistence_s", Unit::Seconds),
                    ("angular_velocity_rad_s", Unit::RadiansPerSecond),
                    ("acceleration_mm_s2", Unit::MillimetersPerSecondSquared),
                    ("donor_median_speed_mm_s", Unit::MillimetersPerSecond),
                    ("donor_p95_speed_mm_s", Unit::MillimetersPerSecond),
                    (
                        "donor_low_motion_threshold_mm_s",
                        Unit::MillimetersPerSecond,
                    ),
                ] {
                    let p = c.parameter(name)?;
                    if p.unit != unit {
                        return Err(ProfileError::Invalid(format!("{name}: wrong units")));
                    }
                    if matches!(unit, Unit::Millimeters | Unit::Seconds) && p.range.min <= 0.0 {
                        return Err(ProfileError::InvalidRange(name.into()));
                    }
                }
                if c.range("donor_median_speed_mm_s")?.min <= 0.0 {
                    return Err(ProfileError::InvalidRange("zero donor median".into()));
                }
                if c.motion_tracks.is_empty() {
                    return Err(ProfileError::MissingField("empirical motion tracks".into()));
                }
                validate_evidence(&c.motion_evidence, "motion_tracks")?;
                for t in &c.motion_tracks {
                    if t.samples.len() < 2
                        || t.track_id.is_empty()
                        || t.species.is_empty()
                        || t.condition.is_empty()
                    {
                        return Err(ProfileError::Invalid(
                            "empty or unidentified motion track".into(),
                        ));
                    }
                    let mut prev = None;
                    for s in &t.samples {
                        if ![
                            s.dt_s,
                            s.speed_mm_s,
                            s.angular_velocity_rad_s,
                            s.acceleration_mm_s2,
                        ]
                        .iter()
                        .all(|x| x.is_finite())
                            || s.dt_s <= 0.0
                            || s.dt_s > 1.0
                            || s.speed_mm_s < 0.0
                        {
                            return Err(ProfileError::Invalid("nonphysical motion sample".into()));
                        }
                        if prev.is_some_and(|p| s.original_frame <= p) {
                            return Err(ProfileError::Invalid("unordered motion frames".into()));
                        }
                        prev = Some(s.original_frame);
                    }
                }
            }
        }
        if !ant {
            return Err(ProfileError::MissingField("ant profile".into()));
        }
        Ok(())
    }
    /// SHA-256 protects the postcard payload. The envelope itself is not signed.
    pub fn encode(&self) -> Result<Vec<u8>, ProfileError> {
        self.validate()?;
        let payload = postcard::to_stdvec(self)?;
        let mut out = Vec::with_capacity(48 + payload.len());
        out.extend_from_slice(PROFILE_MAGIC);
        out.extend_from_slice(&(payload.len() as u64).to_le_bytes());
        out.extend_from_slice(&Sha256::digest(&payload));
        out.extend_from_slice(&payload);
        Ok(out)
    }
    pub fn decode(bytes: &[u8]) -> Result<Self, ProfileError> {
        if bytes.len() < 48 || &bytes[..8] != PROFILE_MAGIC {
            return Err(ProfileError::Digest);
        }
        let len = u64::from_le_bytes(bytes[8..16].try_into().map_err(|_| ProfileError::Digest)?);
        if len != bytes.len() as u64 - 48
            || Sha256::digest(&bytes[48..]).as_slice() != &bytes[16..48]
        {
            return Err(ProfileError::Digest);
        }
        let (bundle, remainder) = postcard::take_from_bytes::<Self>(&bytes[48..])?;
        if !remainder.is_empty() {
            return Err(ProfileError::Digest);
        }
        bundle.validate()?;
        Ok(bundle)
    }
}
