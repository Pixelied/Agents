#![cfg(feature = "native-ui")]
#[test]
fn bounded_soak_exercises_real_lifecycle_and_never_claims_native_acceptance() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("soak.json");
    let result = std::process::Command::new(env!("CARGO_BIN_EXE_desktop-app"))
        .args(["--soak-seconds", "2", "--soak-cycle-frames", "1", "--json"])
        .arg(&path)
        .output()
        .unwrap();
    assert!(
        result.status.success(),
        "{}",
        String::from_utf8_lossy(&result.stderr)
    );
    let report: serde_json::Value = serde_json::from_slice(&std::fs::read(path).unwrap()).unwrap();
    assert_eq!(report["complete"], true);
    assert!(report["wall_seconds"].as_f64().unwrap() >= 2.);
    assert_eq!(report["native_input_verified"], false);
    assert_eq!(report["two_hour_wall_soak_completed"], false);
    assert!(report["events"]["gpu_recreations"].as_u64().unwrap() > 0);
    assert!(report["events"]["topology_changes"].as_u64().unwrap() > 0);
    assert!(report["events"]["config_roundtrips"].as_u64().unwrap() > 0);
    assert_eq!(report["hidden_draws"], 0);
    assert_eq!(report["hidden_state_changes"], 0);
}
