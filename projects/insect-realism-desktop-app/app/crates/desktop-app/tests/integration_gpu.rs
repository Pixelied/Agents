#![cfg(feature = "native-ui")]
//! Real simulation + instance preparation + GPU work. Native window behavior is deliberately not inferred.
use creature_profile::RuntimeProfileBundle;
use desktop_app::{controller::Controller, overlay_host::OverlayHost};
use display_model::*;
use platform_api::*;
use rendering::*;
use std::{sync::Arc, time::Duration};
struct TestWindow(SafetyGate);
impl OverlayWindow for TestWindow {
    fn display_id(&self) -> DisplayId {
        DisplayId(1)
    }
    fn safety_state(&self) -> OverlaySafetyState {
        self.0.state()
    }
    fn verify_input_passthrough(&self) -> Result<(), PlatformError> {
        self.0.verify(NativeSafetyProof {
            transparent: true,
            non_activating: true,
            click_through: true,
            not_task_switcher: true,
        })
    }
    fn set_visible(&self, v: bool) -> Result<(), PlatformError> {
        if v {
            self.0.authorize_show()
        } else {
            self.0.hide();
            Ok(())
        }
    }
    fn is_visible(&self) -> bool {
        self.0.is_visible()
    }
    fn mark_unsafe(&self) {
        self.0.invalidate();
    }
    fn surface_source(&self) -> Result<SurfaceSource, PlatformError> {
        Err(PlatformError::Unsupported(
            "headless test uses no native source",
        ))
    }
}
#[test]
fn thousand_ant_composition_hide_device_loss_and_recreation_use_real_gpu() {
    let profiles = Arc::new(
        RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap(),
    );
    let mut controller = Controller::new(
        settings::AppConfig {
            developer_mode: true,
            target_population: 1100,
            ..Default::default()
        },
        profiles.clone(),
    )
    .unwrap();
    let display = DisplaySurface {
        id: DisplayId(1),
        fingerprint: "gpu-integration".into(),
        name: "Headless fixture".into(),
        pixels: PixelSize {
            width: 1280,
            height: 720,
        },
        desktop_bounds: DesktopRect {
            x: 0.,
            y: 0.,
            width: 1280.,
            height: 720.,
        },
        scale_factor: 1.,
        refresh_hz: 60.,
        rotation_deg: 0,
        calibration: DisplayCalibration::resolve(Some(0.25), None).unwrap(),
    };
    controller
        .set_displays(vec![display.clone()], false)
        .unwrap();
    controller.force_population().unwrap();
    let mut gpu = pollster::block_on(Renderer::new_headless(&profiles.creatures[0])).unwrap();
    let mut builder = InstanceBuilder::new(5000, &profiles.creatures[0]).unwrap();
    let mut host = OverlayHost::new(Box::new(TestWindow(SafetyGate::default())), |_| {
        gpu.offscreen(1280, 720)
    })
    .unwrap();
    host.set_visible(true).unwrap();
    let mut draws = 0;
    let mut minimum_population = usize::MAX;
    let mut maximum_population = 0;
    let mut total_upload = 0;
    // Natural edge exits are part of the production simulation. Start above 1,000
    // and prove every frame still renders at least 1,000 actual living agents;
    // never alter biology merely to maintain a fixed testing population.
    for _ in 0..120 {
        controller
            .advance(Duration::from_nanos(16_666_667), None, host.is_visible())
            .unwrap();
        let instances = builder
            .build(
                controller.simulation().visual_states(),
                &display,
                controller.alpha,
                1.,
            )
            .unwrap();
        let report = host
            .present_if_visible(|target| gpu.render_offscreen(target, instances))
            .unwrap()
            .unwrap();
        let living = controller.simulation().len();
        assert!(living >= 1000, "stress population fell to {living}");
        assert_eq!(report.active_instances as usize, living);
        assert_eq!(report.submitted_draws, 1);
        assert_eq!(report.upload_bytes, living as u64 * 96);
        minimum_population = minimum_population.min(living);
        maximum_population = maximum_population.max(living);
        total_upload += report.upload_bytes;
        draws += report.submitted_draws;
    }
    let frame = gpu.read_rgba(host.surface_mut().unwrap()).unwrap();
    assert!(
        frame
            .as_chunks::<4>()
            .0
            .iter()
            .filter(|p| p[3] > 20)
            .count()
            > 2000
    );
    let frozen = controller.simulation().signature();
    controller.lifecycle.hide_all();
    host.set_visible(false).unwrap();
    controller
        .advance(Duration::from_secs(7200), None, host.is_visible())
        .unwrap();
    assert_eq!(frozen, controller.simulation().signature());
    let mut submitted = false;
    let result = host
        .present_if_visible(|target| {
            submitted = true;
            gpu.render_offscreen(target, &[])
        })
        .unwrap();
    assert!(result.is_none() && !submitted);
    controller.lifecycle.show_all();
    host.set_visible(true).unwrap();
    gpu.device().destroy();
    let _ = gpu.poll();
    assert!(
        host.present_if_visible(|target| gpu.render_offscreen(target, &[]))
            .is_err()
    );
    assert!(!host.is_visible() && !host.safe());
    assert_eq!(frozen, controller.simulation().signature());
    drop(host);
    drop(gpu);
    let mut gpu = pollster::block_on(Renderer::new_headless(&profiles.creatures[0])).unwrap();
    let mut recovered = OverlayHost::new(Box::new(TestWindow(SafetyGate::default())), |_| {
        gpu.offscreen(1280, 720)
    })
    .unwrap();
    assert!(!recovered.is_visible());
    recovered.set_visible(true).unwrap();
    let instances = builder
        .build(
            controller.simulation().visual_states(),
            &display,
            controller.alpha,
            1.,
        )
        .unwrap();
    assert_eq!(
        recovered
            .present_if_visible(|target| gpu.render_offscreen(target, instances))
            .unwrap()
            .unwrap()
            .active_instances,
        controller.simulation().len() as u32
    );
    assert_eq!(frozen, controller.simulation().signature());
    if let Ok(dir) = std::env::var("INSECT_TEST_ARTIFACTS") {
        let path = std::path::Path::new(&dir);
        std::fs::create_dir_all(path).unwrap();
        image::save_buffer(
            path.join("integrated-stress.png"),
            &frame,
            1280,
            720,
            image::ColorType::Rgba8,
        )
        .unwrap();
        std::fs::write(
            path.join("integration.json"),
            serde_json::to_vec_pretty(&serde_json::json!({"frames":120,"initial_ants":1100,
                "minimum_ants":minimum_population,"maximum_ants":maximum_population,
                "final_ants":controller.simulation().len(),"draws":draws,
                "total_upload_bytes":total_upload,
                "backend":format!("{:?}",gpu.adapter_info()),
                "panic_freeze_and_zero_hidden_submissions":true,
                "device_destroy_and_cpu_state_recovery":true,
                "native_input_verified":false,"benchmark_qualification":false}))
            .unwrap(),
        )
        .unwrap();
    }
}
