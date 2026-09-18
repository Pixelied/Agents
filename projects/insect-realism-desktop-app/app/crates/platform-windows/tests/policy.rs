use display_model::DesktopRect;
use platform_api::HotkeyBinding;
use platform_windows::*;
#[test]
fn negative_origin_and_ui_dpi_never_imply_physical_calibration() {
    let d = monitor_surface(
        "monitor-interface".into(),
        "4K".into(),
        DesktopRect {
            x: -3840.,
            y: -200.,
            width: 3840.,
            height: 2160.,
        },
        2.,
        144.,
        0,
    )
    .unwrap();
    assert_eq!(d.pixels.width, 3840);
    assert_eq!(d.desktop_bounds.x, -3840.);
    assert!(!d.calibration.is_ready());
    assert_eq!(d.scale_factor, 2.);
}
#[test]
fn win32_shortcut_codes_are_mapped_explicitly() {
    assert_eq!(
        virtual_key(HotkeyBinding::parse("Ctrl+Alt+Shift+H").unwrap().key),
        Some(0x48)
    );
    assert_eq!(
        virtual_key(HotkeyBinding::parse("Win+F12").unwrap().key),
        Some(0x7B)
    );
}
#[test]
fn run_key_quotes_executable_without_a_shell() {
    assert_eq!(
        autostart_command(r"C:\Program Files\Insect\insect.exe").unwrap(),
        r#""C:\Program Files\Insect\insect.exe""#
    );
    assert!(autostart_command("C:\\bad\"name.exe").is_err());
}
#[test]
fn external_close_is_deferred_until_owned_gpu_surfaces_are_released() {
    assert!(defer_native_close(0x0010));
    assert!(!defer_native_close(0x0082)); // NCDESTROY is real teardown, not a close request.
}
