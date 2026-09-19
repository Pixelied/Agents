use desktop_app::recovery::Recovery;
use std::time::Duration;

#[test]
fn repeated_device_creation_then_loss_cannot_reset_the_retry_budget() {
    let mut recovery = Recovery::default();
    for attempt in 0..3 {
        let now = Duration::from_secs(attempt * 2);
        assert!(recovery.begin(now));
        recovery.succeed();
        recovery.fail(now);
    }
    assert!(
        recovery.exhausted(),
        "device creation is not sustained rendering health"
    );
    assert!(!recovery.begin(Duration::from_secs(999)));
    recovery.retry_by_user();
    assert!(recovery.begin(Duration::from_secs(999)));
}

#[cfg(feature = "native-ui")]
#[test]
fn settings_clear_respects_the_current_theme_without_ui_shapes() {
    let profiles = creature_profile::RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))
    .unwrap();
    let mut gpu =
        pollster::block_on(rendering::Renderer::new_headless(&profiles.creatures[0])).unwrap();
    let target = gpu.offscreen(64, 64).unwrap();
    let mut painter = desktop_app::gui::GuiPainter::new(&gpu, wgpu::TextureFormat::Rgba8Unorm);
    let ctx = egui::Context::default();
    ctx.set_visuals(egui::Visuals::dark());
    let expected = ctx.global_style().visuals.panel_fill.to_array();
    let mut output = ctx.run_ui(
        egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(
                egui::Pos2::ZERO,
                egui::vec2(64., 64.),
            )),
            ..Default::default()
        },
        |_| {},
    );
    painter
        .paint(&gpu, target.view(), &ctx, &mut output, [64, 64])
        .unwrap();
    output.drop_without_applying_deltas();
    let pixels = gpu.read_rgba(&target).unwrap();
    for channel in 0..3 {
        assert!(
            pixels[channel].abs_diff(expected[channel]) <= 1,
            "theme background {:?} was replaced by {:?}",
            expected,
            &pixels[..4]
        );
    }
}
