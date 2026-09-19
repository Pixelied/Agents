#![cfg(feature = "native-ui")]
use std::process::Command;

#[test]
fn benchmark_cli_records_real_frames_and_does_not_claim_native_qualification() {
    let temp = tempfile::tempdir().unwrap();
    let output = temp.path().join("bench.json");
    let result = Command::new(env!("CARGO_BIN_EXE_desktop-app"))
        .args([
            "--benchmark-scenario",
            "stress1000",
            "--benchmark-frames",
            "4",
            "--benchmark-warmup",
            "2",
            "--json",
        ])
        .arg(&output)
        .output()
        .unwrap();
    assert!(
        result.status.success(),
        "{}",
        String::from_utf8_lossy(&result.stderr)
    );
    let report: serde_json::Value =
        serde_json::from_slice(&std::fs::read(output).unwrap()).unwrap();
    assert_eq!(report["mode"], "offscreen_serialized");
    assert_eq!(report["frames"], 4);
    assert_eq!(report["samples"].as_array().unwrap().len(), 4);
    assert!(report["population"]["min"].as_u64().unwrap() >= 1000);
    assert_eq!(report["native_input_verified"], false);
    assert_eq!(report["native_60fps_qualified"], false);
    assert_eq!(report["metrics_ms"]["total"]["count"], 4);
    assert!(report["metrics_ms"]["total"]["p99"].as_f64().unwrap() > 0.0);
    assert!(
        report["samples"]
            .as_array()
            .unwrap()
            .iter()
            .all(|f| f["draws"] == 1)
    );
    assert!(report["last_image_nonzero_alpha_pixels"].as_u64().unwrap() > 100);
    assert!(report["overlay_overhead_ms"].is_null());
}

#[test]
fn invalid_benchmark_requests_fail_before_creating_a_report() {
    let temp = tempfile::tempdir().unwrap();
    let output = temp.path().join("invalid.json");
    for arguments in [
        vec!["--benchmark-scenario", "imaginary", "--json"],
        vec![
            "--benchmark-scenario",
            "stress1000",
            "--benchmark-frames",
            "0",
            "--json",
        ],
    ] {
        let result = Command::new(env!("CARGO_BIN_EXE_desktop-app"))
            .args(arguments)
            .arg(&output)
            .output()
            .unwrap();
        assert!(!result.status.success());
        assert!(!output.exists());
    }
}
