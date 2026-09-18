use display_model::DesktopRect;
use platform_api::HotkeyKey;
/// Cocoa is bottom-left logical points; the shared topology is top-left desktop coordinates.
pub fn desktop_rect(frame: DesktopRect, primary_top: f64) -> DesktopRect {
    DesktopRect {
        y: primary_top - frame.y - frame.height,
        ..frame
    }
}
/// Carbon uses physical ANSI key positions. Labels for non-ANSI layouts are documented separately.
pub fn carbon_key(key: HotkeyKey) -> Option<u32> {
    match key {
        HotkeyKey::Letter(c) => {
            let index = b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".iter().position(|x| *x == c)?;
            Some(
                [
                    0, 11, 8, 2, 14, 3, 5, 4, 34, 38, 40, 37, 46, 45, 31, 35, 12, 15, 1, 17, 32, 9,
                    13, 7, 16, 6,
                ][index],
            )
        }
        HotkeyKey::Digit(n) => [29, 18, 19, 20, 21, 23, 22, 26, 28, 25]
            .get(n as usize)
            .copied(),
        HotkeyKey::Function(n) => n.checked_sub(1).and_then(|i| {
            [122, 120, 99, 118, 96, 97, 98, 100, 101, 109, 103, 111]
                .get(i as usize)
                .copied()
        }),
        HotkeyKey::Escape => Some(53),
        HotkeyKey::Space => Some(49),
    }
}
pub fn login_plist(executable: &str) -> String {
    let path = executable
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;");
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>Label</key><string>com.pixelied.insect-realism</string>
<key>ProgramArguments</key><array><string>{path}</string></array>
<key>RunAtLoad</key><true/>
<key>KeepAlive</key><false/>
<key>ProcessType</key><string>Background</string>
<key>LimitLoadToSessionType</key><string>Aqua</string>
</dict></plist>
"#
    )
}
