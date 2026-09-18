use display_model::DesktopRect;
use platform_api::{HotkeyBinding, HotkeyKey};
use platform_macos::*;
#[test]
fn cocoa_coordinates_are_converted_without_multiplying_ui_scale() {
    let frame = DesktopRect {
        x: -1200.,
        y: -200.,
        width: 1200.,
        height: 800.,
    };
    let r = desktop_rect(frame, 900.);
    assert_eq!(r.x, -1200.);
    assert_eq!(r.y, 300.);
    assert_eq!(r.width, 1200.);
}
#[test]
fn default_panic_binding_maps_to_native_h_key() {
    let b = HotkeyBinding::parse("Ctrl+Alt+Shift+H").unwrap();
    assert_eq!(carbon_key(b.key), Some(4));
    assert_eq!(carbon_key(HotkeyKey::Function(12)), Some(111));
    assert_eq!(carbon_key(HotkeyKey::Function(0)), None);
}
#[test]
fn autostart_xml_escapes_paths_and_never_respawns_after_quit() {
    let x = login_plist("/Applications/A&B\"<app>/Contents/MacOS/Insect");
    assert!(x.contains("A&amp;B&quot;&lt;app&gt;"));
    assert!(x.contains("<key>KeepAlive</key><false/>"));
    assert!(x.contains("<key>LimitLoadToSessionType</key><string>Aqua</string>"));
}

#[test]
fn panic_handler_uses_key_press_instead_of_waiting_for_release() {
    assert_eq!(CARBON_HOTKEY_PRESSED, 5);
    assert_ne!(CARBON_HOTKEY_PRESSED, 6);
}
