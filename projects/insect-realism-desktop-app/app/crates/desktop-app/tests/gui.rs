#![cfg(feature = "native-ui")]
use creature_profile::RuntimeProfileBundle;
use desktop_app::gui::GuiPainter;
#[test]
fn actual_gpu_settings_frame_contains_ui_not_only_a_clear_color() {
    let profile = RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))
    .unwrap();
    let mut gpu =
        pollster::block_on(rendering::Renderer::new_headless(&profile.creatures[0])).unwrap();
    let target = gpu.offscreen(680, 740).unwrap();
    let mut painter = GuiPainter::new(&gpu, wgpu::TextureFormat::Rgba8Unorm);
    let config = settings::AppConfig::default();
    let diag = settings::ui::UiDiagnostics::default();
    let ctx = egui::Context::default();
    let mut settings = settings::ui::SettingsUi::new(&config);
    let mut out = ctx.run_ui(
        egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(
                egui::Pos2::ZERO,
                egui::vec2(680., 740.),
            )),
            ..Default::default()
        },
        |ui| {
            settings.draw(
                ui,
                &settings::ui::SettingsContext {
                    config: &config,
                    displays: &[],
                    profiles: &profile,
                    diagnostics: &diag,
                    recent_apps: &[],
                    preview: None,
                    window_display: None,
                    message: None,
                    read_only: false,
                },
            );
        },
    );
    painter
        .paint(&gpu, target.view(), &ctx, &mut out, [680, 740])
        .unwrap();
    out.drop_without_applying_deltas();
    let pixels = gpu.read_rgba(&target).unwrap();
    let dark = pixels
        .as_chunks::<4>()
        .0
        .iter()
        .filter(|p| p[0] < 140 && p[1] < 140 && p[2] < 140)
        .count();
    assert!(
        dark > 500,
        "GPU frame contained no readable settings; dark pixel count={dark}"
    );
}
