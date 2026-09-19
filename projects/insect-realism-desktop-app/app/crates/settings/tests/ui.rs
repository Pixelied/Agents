#![cfg(feature = "ui")]
use creature_profile::RuntimeProfileBundle;
use display_model::DisplayId;
use settings::{AppConfig, calibration_ui::*, ui::*};
#[test]
fn reference_is_in_backing_pixels_not_ui_points() {
    assert_eq!(reference_points(856., 2.).unwrap(), 428.);
    assert!(reference_points(856., f32::NAN).is_err());
}
#[test]
fn wrong_monitor_and_clipped_reference_cannot_be_saved() {
    assert!(accept_reference(DisplayId(1), Some(DisplayId(2)), 856., true).is_err());
    assert!(accept_reference(DisplayId(1), Some(DisplayId(1)), 856., false).is_err());
    assert!(accept_reference(DisplayId(1), None, 856., true).is_err());
    assert_eq!(
        accept_reference(DisplayId(1), Some(DisplayId(1)), 856., true)
            .unwrap()
            .mm_per_physical_px,
        0.1
    );
}
#[test]
fn real_settings_ui_emits_a_drawable_frame_without_changing_preferences() {
    let config = AppConfig::default();
    let profile = RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))
    .unwrap();
    let diag = UiDiagnostics::default();
    let mut ui = SettingsUi::new(&config);
    let context = SettingsContext {
        config: &config,
        displays: &[],
        profiles: &profile,
        diagnostics: &diag,
        recent_apps: &[],
        preview: None,
        window_display: None,
        message: None,
        read_only: false,
    };
    let ctx = egui::Context::default();
    let input = egui::RawInput {
        screen_rect: Some(egui::Rect::from_min_size(
            egui::Pos2::ZERO,
            egui::vec2(680., 740.),
        )),
        ..Default::default()
    };
    let out = ctx.run_ui(input, |root| {
        assert!(ui.draw(root, &context).is_empty());
    });
    let shapes = out.shapes.len();
    out.drop_without_applying_deltas();
    assert!(shapes > 12, "settings produced no substantive UI");
    assert_eq!(ui.draft(), &config);
}
#[test]
fn opening_settings_with_a_real_display_does_not_mark_the_draft_dirty() {
    use display_model::*;
    let config = AppConfig::default();
    let profiles = RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))
    .unwrap();
    let d = DisplaySurface {
        id: DisplayId(1),
        name: "Panel".into(),
        fingerprint: "fixture".into(),
        pixels: PixelSize {
            width: 1920,
            height: 1080,
        },
        desktop_bounds: DesktopRect {
            x: 0.,
            y: 0.,
            width: 1920.,
            height: 1080.,
        },
        scale_factor: 1.,
        refresh_hz: 60.,
        rotation_deg: 0,
        calibration: DisplayCalibration::resolve(None, Some(0.25)).unwrap(),
    };
    let diagnostics = UiDiagnostics::default();
    let mut ui = SettingsUi::new(&config);
    let ctx = egui::Context::default();
    let out = ctx.run_ui(
        egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(
                egui::Pos2::ZERO,
                egui::vec2(680., 780.),
            )),
            ..Default::default()
        },
        |root| {
            ui.draw(
                root,
                &SettingsContext {
                    config: &config,
                    displays: std::slice::from_ref(&d),
                    profiles: &profiles,
                    diagnostics: &diagnostics,
                    recent_apps: &[],
                    preview: None,
                    window_display: Some(d.id),
                    message: None,
                    read_only: false,
                },
            );
        },
    );
    out.drop_without_applying_deltas();
    assert_eq!(ui.draft(), &config);
}
