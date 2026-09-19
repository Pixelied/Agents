//! Numerical portability regression vectors from the unchanged audited input.
//! These bit patterns specify the pinned software-math compiler, not new biology.
use profile_compiler::{compile, digest, load_input};
use std::path::PathBuf;

fn compiled() -> profile_compiler::Compiled {
    let source = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../assets/creature-profiles/build-input.json");
    compile(&load_input(&source).unwrap()).unwrap()
}

#[test]
fn near_constant_speed_acceleration_is_independent_of_system_libm() {
    let c = compiled();
    let track = c.bundle.creatures[0]
        .motion_tracks
        .iter()
        .find(|t| t.track_id == "H14T001R005:leader")
        .unwrap();
    let sample = &track.samples[1115];
    assert_eq!(sample.original_frame, 11160);
    assert_eq!(sample.acceleration_mm_s2.to_bits(), 2851320205);
}

#[test]
fn near_straight_turn_is_independent_of_system_libm() {
    let c = compiled();
    let track = c.bundle.creatures[0]
        .motion_tracks
        .iter()
        .find(|t| t.track_id == "H14T001R002:leader")
        .unwrap();
    let sample = &track.samples[1581];
    assert_eq!(sample.original_frame, 15820);
    assert_eq!(sample.angular_velocity_rad_s.to_bits(), 2847527010);
}

#[test]
fn audited_snapshot_has_one_byte_identity_on_every_supported_host() {
    let c = compiled();
    assert_eq!(
        digest(&c.bytes),
        "ce558669b3b17d5e85282c1bc94ea8c0e34b6d86311ded3a2acc2d31552c502b"
    );
    assert_eq!(c.bundle.profile_version, "2026.09.17-donor-transfer.2");
}
