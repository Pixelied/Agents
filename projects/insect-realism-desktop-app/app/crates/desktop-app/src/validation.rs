//! Developer-only acceptance scenes. No screen capture or runtime file dependency.
use crate::gui::GuiPainter;
use creature_profile::RuntimeProfileBundle;
use display_model::*;
use rendering::Renderer;
use settings::{AppConfig, ui::*};
use std::path::Path;

pub fn ui_scenes(
    profiles: &RuntimeProfileBundle,
    output: &Path,
) -> Result<(), Box<dyn std::error::Error>> {
    std::fs::create_dir_all(output)?;
    let mut gpu = pollster::block_on(Renderer::new_headless(&profiles.creatures[0]))?;
    let mut reports = Vec::new();
    for (name, ppp, calibration, advanced) in [
        ("settings", 1.0, false, false),
        ("calibration-2x", 2.0, true, false),
        ("developer", 1.0, false, true),
    ] {
        let mut config = AppConfig {
            developer_mode: advanced,
            ..AppConfig::default()
        };
        config.advanced.show_ids = advanced;
        config.advanced.show_grid = advanced;
        config.advanced.show_trails = advanced;
        let display = DisplaySurface {
            id: DisplayId(1),
            fingerprint: "validation-only".into(),
            name: "Validation display".into(),
            pixels: PixelSize {
                width: 2560,
                height: 1440,
            },
            desktop_bounds: DesktopRect {
                x: 0.,
                y: 0.,
                width: 1280.,
                height: 720.,
            },
            scale_factor: 2.,
            refresh_hz: 144.,
            rotation_deg: 0,
            calibration: DisplayCalibration::resolve(Some(0.1), None)?,
        };
        let mut ui = SettingsUi::new(&config);
        ui.set_advanced_open(advanced);
        if calibration {
            ui.open_calibration(&display);
        }
        let diagnostics = UiDiagnostics {
            creature_count: 1000,
            rendered_count: 1000,
            seed: config.advanced.seed,
            ..Default::default()
        };
        let preview = PreviewData {
            display: Some(display.id),
            size_mm: [256., 144.],
            grid_mm: 4.,
            creatures: (0..100)
                .map(|n| PreviewCreature {
                    id: n,
                    position_mm: [(n % 20) as f32 * 12. + 4., (n / 20) as f32 * 25. + 8.],
                    heading: n as f32 * 0.37,
                    behavior: (n % 9) as u8,
                    lod: 1,
                })
                .collect(),
            trails: vec![[20., 30., 0.8], [25., 33., 0.7], [30., 35., 0.5]],
        };
        let context = egui::Context::default();
        context.set_visuals(egui::Visuals::light());
        let points = if advanced {
            [760., 1600.]
        } else {
            [680., 780.]
        };
        let size = [(points[0] * ppp) as u32, (points[1] * ppp) as u32];
        let target = gpu.offscreen(size[0], size[1])?;
        let mut painter = GuiPainter::new(&gpu, wgpu::TextureFormat::Rgba8Unorm);
        for _ in 0..2 {
            let mut input = egui::RawInput {
                screen_rect: Some(egui::Rect::from_min_size(
                    egui::Pos2::ZERO,
                    egui::vec2(points[0], points[1]),
                )),
                ..Default::default()
            };
            input
                .viewports
                .entry(egui::ViewportId::ROOT)
                .or_default()
                .native_pixels_per_point = Some(ppp);
            let mut frame = context.run_ui(input, |root| {
                ui.draw(
                    root,
                    &SettingsContext {
                        config: &config,
                        displays: std::slice::from_ref(&display),
                        profiles,
                        diagnostics: &diagnostics,
                        recent_apps: &[],
                        preview: Some(&preview),
                        window_display: Some(display.id),
                        message: None,
                        read_only: false,
                    },
                );
            });
            if (frame.pixels_per_point - ppp).abs() > 0.001 {
                frame.drop_without_applying_deltas();
                return Err("validation viewport applied DPI scaling twice".into());
            }
            let result = painter.paint(&gpu, target.view(), &context, &mut frame, size);
            frame.drop_without_applying_deltas();
            result?;
        }
        let rgba = gpu.read_rgba(&target)?;
        image::save_buffer(
            output.join(format!("{name}.png")),
            &rgba,
            size[0],
            size[1],
            image::ColorType::Rgba8,
        )?;
        use sha2::Digest;
        reports.push(serde_json::json!({"scene":name,"pixels_per_point":ppp,"pixel_size":size,"profile":profiles.profile_version,"backend":format!("{:?}",gpu.adapter_info()),"rgba_sha256":format!("{:x}",sha2::Sha256::digest(&rgba)),"physical_hardware_verified":false}));
    }
    std::fs::write(
        output.join("ui-validation.json"),
        serde_json::to_vec_pretty(&reports)?,
    )?;
    Ok(())
}
