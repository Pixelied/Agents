#![cfg(feature = "native-ui")]
#[cfg(not(target_os = "windows"))]
#[test]
fn installer_cleanup_is_explicit_and_never_opens_a_desktop_on_other_hosts() {
    let result = std::process::Command::new(env!("CARGO_BIN_EXE_desktop-app"))
        .arg("--uninstall-cleanup")
        .output()
        .unwrap();
    assert!(!result.status.success());
    assert!(String::from_utf8_lossy(&result.stderr).contains("Windows-only installer cleanup"));
}
