//! A deterministic compiler, not a research crawler. Only local audited input is read.
use creature_profile::*;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    path::Path,
};
use thiserror::Error;
pub type Row = BTreeMap<String, String>;
const BIO: &str = "08_DERIVED_BIOLOGY_DATABASE/";
const TRACKS: &str =
    "02_RAW_ANT_DATASETS/tracked_trajectories/valentini-2020/primary_normalized_tracks.csv";
const TABLES: &[&str] = &[
    "00_MASTER_INDEX/SOURCE_CATALOG.csv",
    "00_MASTER_INDEX/SOURCE_REVIEW_STATUS.csv",
    "00_MASTER_INDEX/LICENSE_MANIFEST.csv",
    "08_DERIVED_BIOLOGY_DATABASE/body_dimensions.csv",
    "08_DERIVED_BIOLOGY_DATABASE/stride_parameters.csv",
    "08_DERIVED_BIOLOGY_DATABASE/gait_parameters.csv",
    "08_DERIVED_BIOLOGY_DATABASE/climbing_parameters.csv",
    "08_DERIVED_BIOLOGY_DATABASE/antenna_motion.csv",
    "08_DERIVED_BIOLOGY_DATABASE/interaction_distances.csv",
    "08_DERIVED_BIOLOGY_DATABASE/behavior_states.csv",
    "08_DERIVED_BIOLOGY_DATABASE/walking_speed.csv",
    "08_DERIVED_BIOLOGY_DATABASE/turn_parameters.csv",
    "08_DERIVED_BIOLOGY_DATABASE/stop_durations.csv",
    "08_DERIVED_BIOLOGY_DATABASE/acceleration.csv",
    "03_OTHER_TINY_CREATURES/secondary_species.csv",
];
#[derive(Debug, Error)]
pub enum CompileError {
    #[error("input IO: {0}")]
    Io(#[from] std::io::Error),
    #[error("input JSON: {0}")]
    Json(#[from] serde_json::Error),
    #[error("input CSV: {0}")]
    Csv(#[from] csv::Error),
    #[error("profile: {0}")]
    Profile(#[from] ProfileError),
    #[error("invalid evidence input: {0}")]
    Invalid(String),
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct InputTrack {
    pub track_id: String,
    pub species: String,
    pub points: Vec<[f64; 4]>,
}
/// Restricted material is NOT included. Points are selected CC BY 4.0 leader observations.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct InputSnapshot {
    pub format_version: u32,
    pub pack_manifest_sha256: String,
    pub tables: BTreeMap<String, Vec<Row>>,
    pub input_hashes: BTreeMap<String, String>,
    pub review_ledger: serde_json::Value,
    pub dataset_manifest: serde_json::Value,
    pub tracks: Vec<InputTrack>,
}
#[derive(Serialize, Deserialize)]
struct SnapshotEnvelope {
    sha256: String,
    input: InputSnapshot,
}
pub struct Compiled {
    pub bundle: RuntimeProfileBundle,
    pub bytes: Vec<u8>,
    pub report: serde_json::Value,
}
pub fn digest(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn invalid(s: impl Into<String>) -> CompileError {
    CompileError::Invalid(s.into())
}
fn rows(bytes: &[u8]) -> Result<Vec<Row>, CompileError> {
    let mut csv = csv::Reader::from_reader(bytes);
    let headers = csv.headers()?.clone();
    csv.records()
        .map(|r| {
            Ok(headers
                .iter()
                .zip(r?.iter())
                .map(|(k, v)| (k.to_owned(), v.to_owned()))
                .collect())
        })
        .collect()
}
fn safe_relative(p: &str) -> bool {
    !Path::new(p).is_absolute()
        && !Path::new(p).components().any(|x| {
            matches!(
                x,
                std::path::Component::ParentDir | std::path::Component::Prefix(_)
            )
        })
}
pub fn load_input(path: &Path) -> Result<InputSnapshot, CompileError> {
    if path.is_file() {
        let e: SnapshotEnvelope = serde_json::from_slice(&fs::read(path)?)?;
        if e.input.format_version != 1 || digest(&serde_json::to_vec(&e.input)?) != e.sha256 {
            return Err(invalid("snapshot version/digest mismatch"));
        }
        return Ok(e.input);
    }
    let checksums = fs::read(path.join("00_MASTER_INDEX/CHECKSUMS.txt"))?;
    let mut verified = BTreeMap::new();
    for line in String::from_utf8_lossy(&checksums)
        .lines()
        .filter(|x| !x.trim().is_empty())
    {
        let (hash, name) = line
            .split_once("  ")
            .ok_or_else(|| invalid("malformed checksum entry"))?;
        if !safe_relative(name) {
            return Err(invalid("unsafe checksum path"));
        }
        let bytes = fs::read(path.join(name))?;
        if digest(&bytes) != hash {
            return Err(invalid(format!("checksum mismatch: {name}")));
        }
        verified.insert(name.to_string(), hash.to_string());
    }
    let mut tables = BTreeMap::new();
    let mut input_hashes = BTreeMap::new();
    for name in TABLES {
        let bytes = fs::read(path.join(name))?;
        if !verified.contains_key(*name) {
            return Err(invalid(format!("unverified input {name}")));
        }
        input_hashes.insert(name.to_string(), digest(&bytes));
        tables.insert(name.to_string(), rows(&bytes)?);
    }
    let mut jsons = Vec::new();
    for name in [
        "00_MASTER_INDEX/REVIEW_LEDGER.json",
        "00_MASTER_INDEX/DATASET_MANIFEST.json",
    ] {
        let bytes = fs::read(path.join(name))?;
        input_hashes.insert(name.into(), digest(&bytes));
        jsons.push(serde_json::from_slice::<serde_json::Value>(&bytes)?);
    }
    let bytes = fs::read(path.join(TRACKS))?;
    if !verified.contains_key(TRACKS) {
        return Err(invalid("tracks lack checksum"));
    }
    let hash = digest(&bytes);
    input_hashes.insert(TRACKS.into(), hash.clone());
    let manifest = jsons[1]
        .as_array()
        .ok_or_else(|| invalid("dataset manifest must be array"))?;
    let declared = manifest
        .iter()
        .find(|m| m["normalized_path"] == TRACKS)
        .ok_or_else(|| invalid("unlisted normalized tracks"))?;
    if declared["normalized_sha256"] != hash
        || declared["coordinate_unit"] != "mm"
        || declared["time_unit"] != "s"
    {
        return Err(invalid("trajectory hash or physical-unit mismatch"));
    }
    let mut map: BTreeMap<String, InputTrack> = BTreeMap::new();
    let mut count = 0;
    for row in rows(&bytes)? {
        count += 1;
        if field(&row, "role")? != "leader" {
            continue;
        }
        let id = field(&row, "track_id")?.to_string();
        let species = field(&row, "species")?.to_string();
        if species != "Temnothorax rugatulus" {
            return Err(invalid("unexpected trajectory species"));
        }
        map.entry(id.clone())
            .or_insert_with(|| InputTrack {
                track_id: id,
                species,
                points: Vec::new(),
            })
            .points
            .push([
                number64(&row, "frame")?,
                number64(&row, "time_s")?,
                number64(&row, "x_mm")?,
                number64(&row, "y_mm")?,
            ]);
    }
    if declared["row_count"].as_u64() != Some(count) {
        return Err(invalid("trajectory row-count mismatch"));
    }
    Ok(InputSnapshot {
        format_version: 1,
        pack_manifest_sha256: digest(&checksums),
        tables,
        input_hashes,
        review_ledger: jsons.remove(0),
        dataset_manifest: jsons.remove(0),
        tracks: map.into_values().collect(),
    })
}
pub fn export_input(input: &InputSnapshot, path: &Path) -> Result<(), CompileError> {
    let e = SnapshotEnvelope {
        sha256: digest(&serde_json::to_vec(input)?),
        input: input.clone(),
    };
    fs::write(path, serde_json::to_vec(&e)?)?;
    Ok(())
}
fn field<'a>(row: &'a Row, key: &str) -> Result<&'a str, CompileError> {
    row.get(key)
        .filter(|v| !v.is_empty())
        .map(String::as_str)
        .ok_or_else(|| invalid(format!("missing source field {key}")))
}
fn number64(row: &Row, key: &str) -> Result<f64, CompileError> {
    let n = field(row, key)?
        .parse::<f64>()
        .map_err(|_| invalid(format!("not numeric {key}")))?;
    if !n.is_finite() {
        Err(invalid(format!("nonfinite {key}")))
    } else {
        Ok(n)
    }
}
fn number(row: &Row, key: &str) -> Result<f32, CompileError> {
    let n = number64(row, key)? as f32;
    if n.is_finite() {
        Ok(n)
    } else {
        Err(invalid(format!("overflow {key}")))
    }
}
fn table<'a>(input: &'a InputSnapshot, name: &str) -> Result<&'a [Row], CompileError> {
    input
        .tables
        .get(name)
        .map(Vec::as_slice)
        .ok_or_else(|| invalid(format!("missing source table {name}")))
}
fn selected<'a>(
    input: &'a InputSnapshot,
    name: &str,
    predicate: impl Fn(&Row) -> bool,
) -> Result<&'a Row, CompileError> {
    let mut matches = table(input, name)?.iter().filter(|r| predicate(r));
    let first = matches
        .next()
        .ok_or_else(|| invalid(format!("no matching source row {name}")))?;
    if matches.next().is_some() {
        return Err(invalid(format!("ambiguous source row {name}")));
    }
    Ok(first)
}
fn eq(row: &Row, k: &str, v: &str) -> bool {
    row.get(k).is_some_and(|x| x == v)
}
fn donor(row: &Row) -> bool {
    eq(row, "behavior_state", "tandem-leader") && eq(row, "sampling_frames", "10")
}
fn source_ref(
    input: &InputSnapshot,
    file: &str,
    fields: &str,
    ids: &str,
    basis: EvidenceBasis,
    note: &str,
) -> Result<Vec<EvidenceRef>, CompileError> {
    let hash = input
        .input_hashes
        .get(file)
        .ok_or_else(|| invalid(format!("source file hash missing: {file}")))?;
    let mut out = Vec::new();
    for id in ids.split(';') {
        let _catalog = selected(input, "00_MASTER_INDEX/SOURCE_CATALOG.csv", |r| {
            eq(r, "source_id", id)
        })?;
        let license = selected(input, "00_MASTER_INDEX/LICENSE_MANIFEST.csv", |r| {
            eq(r, "source_id", id)
        })?;
        let class = field(license, "license_class")?;
        if class == "DO_NOT_USE" {
            return Err(invalid(format!("prohibited evidence {id}")));
        }
        let status = table(input, "00_MASTER_INDEX/SOURCE_REVIEW_STATUS.csv")?
            .iter()
            .find(|r| eq(r, "source_id", id))
            .and_then(|r| r.get("review_state"))
            .map(String::as_str);
        let ledger = input
            .review_ledger
            .as_array()
            .and_then(|a| a.iter().find(|r| r["source_id"] == id))
            .and_then(|r| r["review_state"].as_str());
        let review = match (status, ledger) {
            (Some(a), Some(b)) if a != b => format!("conflicting: status={a}; ledger={b}"),
            (Some(a), _) => a.into(),
            (_, Some(b)) => b.into(),
            _ => "not recorded".into(),
        };
        out.push(EvidenceRef{source_id:id.into(),source_file:file.into(),source_field:fields.into(),source_sha256:hash.clone(),context:if id.contains("valentini"){"Temnothorax rugatulus; tandem-leader; first 900 s; every 10 frames; 29.97 FPS; 20 pairs / 6 reported colonies. Correlated observations.".into()}else{"Source-specific Argentine-ant measurements or explicitly identified donor mechanism; see parameter note.".into()},basis,note:note.into(),review_state:review,license_class:class.into()});
    }
    Ok(out)
}
fn engineering(name: &str, value: (f32, f32), unit: Unit, note: &str) -> (String, Parameter) {
    (
        name.into(),
        Parameter {
            range: RangeF32 {
                min: value.0,
                max: value.1,
            },
            unit,
            evidence: vec![EvidenceRef {
                source_id: format!("engineering:{name}"),
                source_file: "profile-compiler/src/lib.rs".into(),
                source_field: name.into(),
                source_sha256: String::new(),
                context:
                    "Numerical/rendering design assumption, NOT a measured biological constant"
                        .into(),
                basis: EvidenceBasis::EngineeringAssumption,
                note: note.into(),
                review_state: "engineering choice".into(),
                license_class: "ORIGINAL_IMPLEMENTATION".into(),
            }],
        },
    )
}
#[expect(
    clippy::too_many_arguments,
    reason = "Explicit source-column and scientific-context mapping kept together for auditability"
)]
fn measured(
    input: &InputSnapshot,
    file: &str,
    row: &Row,
    lo: &str,
    hi: &str,
    unit: Unit,
    basis: EvidenceBasis,
    note: &str,
) -> Result<Parameter, CompileError> {
    Ok(Parameter {
        range: RangeF32 {
            min: number(row, lo)?,
            max: number(row, hi)?,
        },
        unit,
        evidence: source_ref(
            input,
            file,
            &format!("{lo};{hi}"),
            field(row, "source_ids")?,
            basis,
            note,
        )?,
    })
}
fn quantile(values: &[f64], p: f64) -> Result<f64, CompileError> {
    if values.is_empty() {
        return Err(invalid("empty empirical distribution"));
    }
    let mut v = values.to_vec();
    v.sort_by(f64::total_cmp);
    let t = (v.len() - 1) as f64 * p;
    let i = t.floor() as usize;
    Ok(v[i] + (v[(i + 1).min(v.len() - 1)] - v[i]) * (t - i as f64))
}
pub fn compile(input: &InputSnapshot) -> Result<Compiled, CompileError> {
    if input.format_version != 1 {
        return Err(invalid("unsupported input schema"));
    }
    let mut parameters = BTreeMap::new();
    let body = format!("{BIO}body_dimensions.csv");
    for (name, measurement) in [
        ("body_length_mm", "total_worker_body_length"),
        ("head_length_mm", "head_length"),
        ("head_width_mm", "head_width"),
    ] {
        let row = selected(input, &body, |r| {
            eq(r, "species_id", "linepithema-humile") && eq(r, "measurement", measurement)
        })?;
        parameters.insert(name.into(),measured(input,&body,row,"min_mm","max_mm",Unit::Millimeters,EvidenceBasis::Measurement,
   "AntWeb taxon-aggregate range, not a measured colony distribution. Uniform sampling within this range is an engineering prior; segment covariance unavailable. No AntWeb imagery is redistributed.")?);
    }
    let stride_file = format!("{BIO}stride_parameters.csv");
    let stride = selected(input, &stride_file, |r| {
        eq(r, "species_id", "linepithema-humile") && eq(r, "state", "flat-walk")
    })?;
    parameters.insert("stride_length_mm".into(),measured(input,&stride_file,stride,"stride_length_min_mm","stride_length_max_mm",Unit::Millimeters,EvidenceBasis::Measurement,"Flat-ground primarily reported stride range. Distribution and duty factor not supplied; uniform persistent trait is an engineering prior.")?);
    parameters.insert("preferred_speed_mm_s".into(),measured(input,&stride_file,stride,"speed_bin_mm_s","speed_bin_mm_s",Unit::MillimetersPerSecond,EvidenceBasis::Measurement,"15.1 is the reported preferred/median flat speed, not a universal speed bin, mean, or measured speed distribution.")?);
    let climb_file = format!("{BIO}climbing_parameters.csv");
    for (name, orientation) in [
        ("climb_up_speed_mm_s", "upward vertical"),
        ("climb_down_speed_mm_s", "downward vertical"),
    ] {
        let r = selected(input, &climb_file, |r| {
            eq(r, "orientation", orientation) && eq(r, "species_id", "linepithema-humile")
        })?;
        parameters.insert(name.into(),measured(input,&climb_file,r,"speed_mean_mm_s","speed_mean_mm_s",Unit::MillimetersPerSecond,EvidenceBasis::Measurement,"Reported condition-specific mean; smooth tested substrate, not every monitor coating. Interpolating with heading is an engineering model. Thesis bytes are not included.")?);
    }
    let speed_file = format!("{BIO}walking_speed.csv");
    let speed = selected(input, &speed_file, donor)?;
    if field(speed, "unit")? != "mm/s" {
        return Err(invalid("walking speed unit must be mm/s"));
    }
    for (name, col) in [
        ("donor_median_speed_mm_s", "median"),
        ("donor_p95_speed_mm_s", "p95"),
    ] {
        parameters.insert(name.into(),measured(input,&speed_file,speed,col,col,Unit::MillimetersPerSecond,EvidenceBasis::DerivedMeasurement,"Empirical donor centroid-displacement distribution. P95 truncation and median rescaling are explicit engineering transfer, not Argentine-ant measurements.")?);
    }
    let stop_file = format!("{BIO}stop_durations.csv");
    let stop = selected(input, &stop_file, |r| {
        donor(r) && eq(r, "threshold_percentile", "10")
    })?;
    if field(stop, "unit")? != "s" {
        return Err(invalid("stop durations must be seconds"));
    }
    parameters.insert("donor_low_motion_threshold_mm_s".into(),measured(input,&stop_file,stop,"threshold_mm_s","threshold_mm_s",Unit::MillimetersPerSecond,EvidenceBasis::DerivedMeasurement,"Operational donor low-motion threshold (10th percentile); not proof of biological rest.")?);
    parameters.insert("pause_duration_s".into(),measured(input,&stop_file,stop,"p05","p95",Unit::Seconds,EvidenceBasis::DonorTransfer,"Central 90% uncensored operational low-motion bout durations. Transfer only, not species-matched pause or antennation measurements.")?);
    let turn_file = format!("{BIO}turn_parameters.csv");
    let turns = selected(input, &turn_file, donor)?;
    if field(turns, "unit")? != "rad/s" {
        return Err(invalid("turn units must be rad/s"));
    }
    let mut angular = measured(
        input,
        &turn_file,
        turns,
        "p95",
        "p95",
        Unit::RadiansPerSecond,
        EvidenceBasis::DonorTransfer,
        "Absolute donor p95 mirrored into a signed steering bound. Original signs are preserved in each motion clip; no new turn distribution is fabricated.",
    )?;
    angular.range.min = -angular.range.max;
    parameters.insert("angular_velocity_rad_s".into(), angular);
    let acceleration_file = format!("{BIO}acceleration.csv");
    let accel = selected(input, &acceleration_file, donor)?;
    if field(accel, "unit")? != "mm/s^2" {
        return Err(invalid("acceleration units must be mm/s^2"));
    }
    parameters.insert("acceleration_mm_s2".into(),measured(input,&acceleration_file,accel,"p05","p95",Unit::MillimetersPerSecondSquared,EvidenceBasis::DonorTransfer,"Signed central donor acceleration interval; use median speed scaling when transferred. Negative deceleration is valid, not malformed data.")?);
    let license = selected(input, "00_MASTER_INDEX/LICENSE_MANIFEST.csv", |r| {
        eq(r, "source_id", "dataset-valentini-2020-tandem")
    })?;
    if !eq(license, "shipping_allowed", "true") {
        return Err(invalid("raw trajectory reuse not permitted"));
    }
    let mut motion_tracks = Vec::new();
    let mut speeds = Vec::new();
    let mut run_durations = Vec::new();
    let mut excluded_undefined_turns = 0;
    let mut high_speed_count = 0;
    let turn_threshold = number64(turns, "median")?;
    let low = number64(stop, "threshold_mm_s")?;
    let speed_p95 = number64(speed, "p95")?;
    for track in &input.tracks {
        if track.species != "Temnothorax rugatulus" || !track.track_id.ends_with(":leader") {
            return Err(invalid("snapshot contains unselected donor condition"));
        }
        let mut samples = Vec::new();
        let mut prev_heading = None;
        let mut prev_speed = None;
        let mut run = 0.0;
        for pair in track.points.windows(2) {
            let [frame0, t0, x0, y0] = pair[0];
            let [frame, t, x, y] = pair[1];
            let dt = t - t0;
            if !pair.iter().flatten().all(|v| v.is_finite())
                || dt <= 0.0
                || dt > 0.338
                || frame - frame0 != 10.0
                || frame.fract() != 0.0
                || frame < 0.0
                || frame > u32::MAX as f64
            {
                return Err(invalid("invalid trajectory interval/units/frame"));
            }
            let dx = x - x0;
            let dy = y - y0;
            // std transcendental functions use platform libm. Tiny differences
            // survive cancellation in acceleration, breaking exact profile bytes.
            // Pin the software implementation; do not round or discard measurements.
            let v = libm::hypot(dx, dy) / dt;
            let heading = if v > 0.0 {
                Some(libm::atan2(dy, dx))
            } else {
                None
            };
            let omega = match (heading, prev_heading) {
                (Some(h), Some(p)) => {
                    let d: f64 = h - p;
                    libm::atan2(libm::sin(d), libm::cos(d)) / dt
                }
                _ => {
                    excluded_undefined_turns += 1;
                    0.0
                }
            };
            let acceleration = prev_speed.map_or(0.0, |p| (v - p) / dt);
            if v > speed_p95 {
                high_speed_count += 1;
            }
            speeds.push(v);
            if omega.abs() < turn_threshold && v > low {
                run += dt;
            } else if run > 0.0 {
                run_durations.push(run);
                run = 0.0;
            }
            samples.push(MotionSample {
                dt_s: dt as f32,
                speed_mm_s: v as f32,
                angular_velocity_rad_s: omega as f32,
                acceleration_mm_s2: acceleration as f32,
                original_frame: frame as u32,
            });
            prev_heading = heading;
            prev_speed = Some(v);
        }
        if run > 0.0 {
            run_durations.push(run);
        }
        motion_tracks.push(MotionTrack{track_id:track.track_id.clone(),species:track.species.clone(),condition:"laboratory tandem emigration; leader; 10 original frames; <900 s; 29.97 FPS".into(),samples});
    }
    motion_tracks.sort_by(|a, b| a.track_id.cmp(&b.track_id));
    let median = quantile(&speeds, 0.5)?;
    if (median - number64(speed, "median")?).abs() > 1e-7
        || speeds.len() != number64(speed, "count")? as usize
    {
        return Err(invalid(
            "raw-track statistics do not match selected derived evidence",
        ));
    }
    let motion_evidence = source_ref(
        input,
        TRACKS,
        "track_id;role;frame;time_s;x_mm;y_mm",
        "dataset-valentini-2020-tandem",
        EvidenceBasis::DonorTransfer,
        "Retain contiguous signed speed/turn sequences, not random waypoints/noise. First/zero-displacement undefined headings receive zero turn and are counted. Runtime p95 winsorization limits tracking outliers; all unmodified samples remain in this bundle. Transfer and smooth interpolation are engineering choices.",
    )?;
    parameters.insert("direction_persistence_s".into(),Parameter{range:RangeF32{min:quantile(&run_durations,0.05)? as f32,max:quantile(&run_durations,0.95)? as f32},unit:Unit::Seconds,evidence:source_ref(input,TRACKS,"contiguous abs(turn_rate)<donor median and speed>donor p10; p05/p95 duration","dataset-valentini-2020-tandem",EvidenceBasis::DerivedMeasurement,"Operational run definition uses the donor median turn-rate threshold, not observed behavioral labels. Context-only transfer; trajectories retain empirical temporal correlation.")?});
    for (name, range, unit, note) in [
        (
            "body_width_ratio_to_head",
            (1.0, 1.0),
            Unit::Scalar,
            "Missing whole-body width: use head-width silhouette envelope as a rendering proxy, not a measured whole-body dimension.",
        ),
        (
            "speed_variation_fraction",
            (0.12, 0.12),
            Unit::Scalar,
            "Small stable individual offset around the reported preferred speed; no species-matched variance was supplied. Engineering variability, not measured SD.",
        ),
        (
            "velocity_filter_s",
            (0.09, 0.09),
            Unit::Seconds,
            "Short interpolation/filter time to avoid discontinuous velocity at sampled clip boundaries. Numerical choice, not a biological response time.",
        ),
        (
            "antenna_target_interval_s",
            (0.18, 0.42),
            Unit::Seconds,
            "Correlated target cadence chosen for visual temporal continuity. Pack supplies no numeric sweep frequency; not measured.",
        ),
        (
            "antenna_sweep_rad",
            (0.18, 1.12),
            Unit::Radians,
            "Bounded articulated antenna geometry for probing. Qualitative donor mode only; amplitude is an engineering rendering assumption.",
        ),
        (
            "antenna_filter_s",
            (0.10, 0.10),
            Unit::Seconds,
            "Smooth asymmetric target interpolation, not an experimentally measured time constant.",
        ),
        (
            "encounter_radius_body_lengths",
            (1.2, 1.2),
            Unit::Scalar,
            "Morphometric contact/proximity envelope only. Pack has no calibrated social-interaction distance.",
        ),
        (
            "encounter_response_probability",
            (0.24, 0.24),
            Unit::Scalar,
            "Sparse context response to avoid every neighbor causing a stop; unmeasured engineering state policy.",
        ),
        (
            "edge_zone_body_lengths",
            (2.0, 2.0),
            Unit::Scalar,
            "Look-ahead proportional to body length prevents clipping while preserving edge-probing motion; not measured edge preference.",
        ),
        (
            "edge_exit_probability",
            (0.08, 0.08),
            Unit::Scalar,
            "Population-management policy at exterior boundaries, not a measured emigration hazard.",
        ),
        (
            "trail_decay_s",
            (30.0, 30.0),
            Unit::Seconds,
            "Coarse visual route-memory time; not a measured chemical decay constant. No pheromone concentration units are claimed.",
        ),
        (
            "trail_cell_mm",
            (4.0, 4.0),
            Unit::Millimeters,
            "Coarse numerical field resolution, not a biological interaction length.",
        ),
        (
            "trail_turn_weight",
            (0.35, 0.35),
            Unit::Scalar,
            "Limited steering influence for a phenomenological field. Quantitative species-matched response unavailable.",
        ),
        (
            "cursor_radius_mm",
            (12.0, 12.0),
            Unit::Millimeters,
            "Optional artificial disturbance envelope; not ant response to a real physical cursor. OFF by default.",
        ),
        (
            "gait_stance_fraction",
            (0.5, 0.5),
            Unit::Scalar,
            "Alternating-tripod phase construction. Source does not supply a numeric duty factor; symmetric stance is an animation assumption.",
        ),
        (
            "leg_reach_body_fraction",
            (0.45, 0.45),
            Unit::Scalar,
            "Procedural appendage envelope; no calibrated segment lengths in the Pack. Renderer assumption requiring 1:1 review.",
        ),
        (
            "thorax_length_fraction",
            (0.25, 0.25),
            Unit::Scalar,
            "Procedural segmented silhouette allocation; not a measured mesosoma fraction.",
        ),
        (
            "abdomen_length_fraction",
            (0.38, 0.38),
            Unit::Scalar,
            "Procedural gaster silhouette allocation; not a measured anatomical fraction.",
        ),
        (
            "leg_thickness_mm",
            (0.025, 0.04),
            Unit::Millimeters,
            "Coverage-filtered appendage width for tiny on-glass appearance; calibrated width measurements absent.",
        ),
    ] {
        let (k, p) = engineering(name, range, unit, note);
        parameters.insert(k, p);
    }
    // Attach the qualitative research basis without pretending its missing numerical values exist.
    for (parameter, file, ids, fields, note) in [
        (
            "antenna_sweep_rad",
            "antenna_motion.csv",
            "paper-draft-2018-antennae",
            "state;head_coupling_observation;source_ids",
            "Camponotus donor qualitative asymmetric/anti-correlated sampling modes; numerical angle/cadence remain engineering assumptions.",
        ),
        (
            "gait_stance_fraction",
            "gait_parameters.csv",
            "paper-clifton-2020-uneven;paper-reinhardt-2009-locomotion",
            "phase_group_a;phase_group_b",
            "Alternating tripod topology is research-backed; numeric stance fraction and geometry are not.",
        ),
        (
            "trail_turn_weight",
            "behavior_states.csv",
            "paper-perna-2012-trail-pattern;paper-choe-2012-trail-pheromone",
            "state;source_ids",
            "Qualitative trail-following mechanism only; visual field values are dimensionless engineering controls.",
        ),
    ] {
        // The source identifier must actually exist in the provided catalog.
        let file = format!("{BIO}{file}");
        let refs = source_ref(
            input,
            &file,
            fields,
            ids,
            EvidenceBasis::DonorTransfer,
            note,
        )?;
        parameters
            .get_mut(parameter)
            .ok_or_else(|| invalid(parameter))?
            .evidence
            .extend(refs);
    }
    let warnings=vec![
  "Morphology: AntWeb 2.2-2.6 mm workers vs ~3 mm in Clifton experimental context are not averaged; default uses AntWeb range, validation includes intentional 2/3/4 mm scenes.".into(),
  "Trajectory donor is Temnothorax rugatulus tandem leader, not Argentine-ant exploratory or vertical trajectories. Transfer model remains biologically unvalidated.".into(),
  "SOURCE_REVIEW_STATUS.csv and REVIEW_LEDGER.json disagree for some sources. Both states remain visible in provenance; no confidence upgrade inferred.".into(),
  "Antenna amplitudes, detailed appendage anatomy, interaction probabilities and pheromone decay are engineering assumptions because numeric evidence is absent.".into(),
  "Snapshot contains original derived facts and CC BY 4.0 selected coordinates only, no restricted imagery, thesis/article/video bytes or reference-only code.".into(),
 ];
    let bundle = RuntimeProfileBundle {
        schema_version: 1,
        profile_version: "2026.09.17-donor-transfer.2".into(),
        pack_sha256: input.pack_manifest_sha256.clone(),
        creatures: vec![CreatureProfile {
            id: CreatureKind("ant".into()),
            species: "Linepithema humile".into(),
            qualified: true,
            parameters,
            motion_tracks,
            motion_evidence,
            limitations: warnings.clone(),
        }],
        input_hashes: input.input_hashes.clone(),
        warnings,
    };
    let bytes = bundle.encode()?;
    let mut ids = BTreeSet::new();
    for c in &bundle.creatures {
        for p in c.parameters.values() {
            for r in &p.evidence {
                ids.insert(r.source_id.clone());
            }
        }
        for r in &c.motion_evidence {
            ids.insert(r.source_id.clone());
        }
    }
    let report = serde_json::json!({"schema_version":1,"profile_version":bundle.profile_version,"sha256":digest(&bytes),"pack_manifest_sha256":bundle.pack_sha256,"creature_ids":["ant"],"source_ids":ids,"input_files":bundle.input_hashes,"selected_condition":"Temnothorax rugatulus tandem-leader, first 900 seconds, every tenth original frame","track_count":bundle.creatures[0].motion_tracks.len(),"interval_count":speeds.len(),"donor_speed_median_recomputed":median,"undefined_heading_intervals_zeroed":excluded_undefined_turns,"runtime_p95_winsorized_intervals":high_speed_count,"operational_run_count":run_durations.len(),"provenance":bundle.creatures[0].parameters,"warnings":bundle.warnings});
    Ok(Compiled {
        bundle,
        bytes,
        report,
    })
}
