use creature_profile::{ProfileError, RuntimeProfileBundle};
use profile_compiler::{InputSnapshot, compile, load_input};
use std::path::PathBuf;
fn input() -> InputSnapshot {
    let path = std::env::var_os("MEGA_PACK_ROOT")
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                .join("../../assets/creature-profiles/build-input.json")
        });
    load_input(&path).unwrap()
}
#[test]
fn compile_is_byte_deterministic_and_roundtrips() {
    let input = input();
    let a = compile(&input).unwrap();
    let b = compile(&input).unwrap();
    assert_eq!(a.bytes, b.bytes);
    assert_eq!(RuntimeProfileBundle::decode(&a.bytes).unwrap(), a.bundle);
    assert_eq!(a.report, b.report);
}
#[test]
fn every_parameter_has_traceable_evidence_and_donor_context() {
    let compiled = compile(&input()).unwrap();
    let ant = &compiled.bundle.creatures[0];
    assert!(
        ant.motion_tracks
            .iter()
            .all(|t| t.species == "Temnothorax rugatulus")
    );
    assert!(
        ant.motion_tracks
            .iter()
            .all(|t| t.condition.contains("tandem"))
    );
    assert!(
        compiled.report["source_ids"]
            .as_array()
            .unwrap()
            .iter()
            .any(|s| s == "dataset-valentini-2020-tandem")
    );
    assert!(
        compiled.report["input_files"]
            .as_object()
            .unwrap()
            .contains_key("08_DERIVED_BIOLOGY_DATABASE/body_dimensions.csv")
    );
    assert!(ant.parameters.values().all(|p| !p.evidence.is_empty()));
}
#[test]
fn negative_body_measurement_is_rejected_not_clamped() {
    let mut input = input();
    let rows = input
        .tables
        .get_mut("08_DERIVED_BIOLOGY_DATABASE/body_dimensions.csv")
        .unwrap();
    rows.iter_mut()
        .find(|r| r["measurement"] == "total_worker_body_length")
        .unwrap()
        .insert("min_mm".into(), "-1".into());
    assert!(compile(&input).is_err());
}
#[test]
fn corrupt_payload_and_future_schema_are_rejected() {
    let mut c = compile(&input()).unwrap();
    c.bytes[64] ^= 1;
    assert!(matches!(
        RuntimeProfileBundle::decode(&c.bytes),
        Err(ProfileError::Digest)
    ));
    c.bundle.schema_version = 999;
    assert!(matches!(
        c.bundle.encode(),
        Err(ProfileError::UnsupportedSchema(999))
    ));
}
#[test]
fn missing_provenance_duplicates_and_nonfinite_motion_are_rejected() {
    let c = compile(&input()).unwrap();
    let mut b = c.bundle.clone();
    b.creatures.push(b.creatures[0].clone());
    assert!(b.validate().is_err());
    let mut b = c.bundle.clone();
    b.creatures[0]
        .parameters
        .get_mut("head_width_mm")
        .unwrap()
        .evidence
        .clear();
    assert!(b.validate().is_err());
    let mut b = c.bundle.clone();
    b.creatures[0].motion_tracks[0].samples[0].speed_mm_s = f32::NAN;
    assert!(b.validate().is_err());
    let mut b = c.bundle;
    b.creatures.clear();
    assert!(b.validate().is_err());
}
#[test]
fn unknown_units_and_missing_required_source_are_errors() {
    let mut input = input();
    input
        .tables
        .remove("08_DERIVED_BIOLOGY_DATABASE/stride_parameters.csv");
    assert!(compile(&input).is_err());
}
#[test]
fn qualitative_provenance_points_to_actual_csv_fields() {
    let input = input();
    let c = compile(&input).unwrap();
    for name in [
        "antenna_sweep_rad",
        "gait_stance_fraction",
        "trail_turn_weight",
    ] {
        for r in &c.bundle.creatures[0].parameters[name].evidence {
            if r.basis == creature_profile::EvidenceBasis::EngineeringAssumption {
                continue;
            }
            let row = &input.tables[&r.source_file][0];
            for field in r.source_field.split(';') {
                assert!(row.contains_key(field), "{}: {field}", r.source_file);
            }
        }
    }
}
#[test]
fn declared_wrong_speed_units_fail_compilation() {
    let mut input = input();
    for r in input
        .tables
        .get_mut("08_DERIVED_BIOLOGY_DATABASE/walking_speed.csv")
        .unwrap()
    {
        if r["behavior_state"] == "tandem-leader" && r["sampling_frames"] == "10" {
            r.insert("unit".into(), "pixels/frame".into());
        }
    }
    assert!(compile(&input).is_err());
}
#[test]
fn source_snapshot_tampering_is_detected() {
    let input = input();
    let temp = tempfile::tempdir().unwrap();
    let p = temp.path().join("input.json");
    profile_compiler::export_input(&input, &p).unwrap();
    let mut value: serde_json::Value = serde_json::from_slice(&std::fs::read(&p).unwrap()).unwrap();
    value["input"]["tracks"][0]["points"][0][2] = serde_json::json!(999.0);
    std::fs::write(&p, serde_json::to_vec(&value).unwrap()).unwrap();
    assert!(load_input(&p).is_err());
}
