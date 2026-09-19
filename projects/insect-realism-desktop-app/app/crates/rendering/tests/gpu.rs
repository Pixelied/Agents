use creature_profile::RuntimeProfileBundle;
use rendering::*;
fn profile() -> RuntimeProfileBundle {
    RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))
    .unwrap()
}
fn ant() -> CreatureRenderInstance {
    CreatureRenderInstance {
        position_px: [64., 64.],
        heading_rad: 0.,
        length_px: 30.,
        width_px: 6.5,
        head_length_px: 8.,
        head_width_px: 6.5,
        gait_phase: 0.2,
        antenna: [0.65, 0.7],
        speed_norm: 1.,
        turn_amount: 0.,
        pose_blend: 0.,
        stride_px: 22.,
        leg_radius_px: 0.15,
        padding: 0.,
        morphology_seed: 42,
        lod: 1,
        behavior: 0,
        flags: 0,
        material: [0.012, 0.009, 0.006, 0.98],
    }
}
#[test]
fn wgsl_compiles_and_validates() {
    let m = naga::front::wgsl::parse_str(include_str!("../shaders/ant.wgsl")).unwrap();
    naga::valid::Validator::new(
        naga::valid::ValidationFlags::all(),
        naga::valid::Capabilities::all(),
    )
    .validate(&m)
    .unwrap();
}
#[test]
fn actual_gpu_transparent_clear_and_anatomy() {
    let p = profile();
    let mut r = pollster::block_on(Renderer::new_headless(&p.creatures[0]))
        .expect("GPU validation needs Vulkan/Metal/D3D12; do not substitute image mocks");
    let target = r.offscreen(128, 128).unwrap();
    r.render_offscreen(&target, &[]).unwrap();
    assert!(r.read_rgba(&target).unwrap().iter().all(|x| *x == 0));
    let stats = r.render_offscreen(&target, &[ant()]).unwrap();
    let pixels = r.read_rgba(&target).unwrap();
    assert_eq!(stats.submitted_draws, 1);
    assert_eq!(stats.upload_bytes, 96);
    let coverage: f64 = pixels
        .as_chunks::<4>()
        .0
        .iter()
        .map(|p| f64::from(p[3]) / 255.)
        .sum();
    assert!(coverage > 80., "missing ant body/limbs: {coverage}");
    assert!(
        coverage < 500.,
        "oversized shadow/opaque rectangle: {coverage}"
    );
    assert_eq!(pixels[3], 0);
    r.check_health().unwrap();
}
#[test]
fn gpu_rotation_and_subpixel_coverage_do_not_disappear() {
    let p = profile();
    let mut r = pollster::block_on(Renderer::new_headless(&p.creatures[0])).unwrap();
    let target = r.offscreen(128, 128).unwrap();
    for ppi in [96., 144., 220.] {
        let mut totals = vec![];
        let scale = ppi / 254.;
        for deg in (0..360).step_by(10) {
            let mut a = ant();
            a.heading_rad = (deg as f32).to_radians();
            a.length_px *= scale;
            a.width_px *= scale;
            a.head_length_px *= scale;
            a.head_width_px *= scale;
            a.stride_px *= scale;
            a.leg_radius_px *= scale;
            a.lod = select_lod(a.length_px, None) as u32;
            r.render_offscreen(&target, &[a]).unwrap();
            let px = r.read_rgba(&target).unwrap();
            let sum: f64 = px
                .as_chunks::<4>()
                .0
                .iter()
                .map(|p| f64::from(p[3]) / 255.)
                .sum();
            assert!(sum > 10., "ant vanished at {ppi}PPI/{deg}deg");
            totals.push(sum);
        }
        let min = totals.iter().copied().fold(f64::INFINITY, f64::min);
        let max = totals.iter().copied().fold(0., f64::max);
        assert!(max / min < 1.32, "rotation shimmer {ppi}PPI: {min}..{max}");
    }
}
#[test]
fn all_eight_appendages_exist_in_gpu_diagnostic() {
    let p = profile();
    let mut r = pollster::block_on(Renderer::new_headless(&p.creatures[0])).unwrap();
    let target = r.offscreen(512, 512).unwrap();
    let mut a = ant();
    a.position_px = [256., 256.];
    a.length_px *= 6.;
    a.width_px *= 6.;
    a.head_length_px *= 6.;
    a.head_width_px *= 6.;
    a.stride_px *= 6.;
    a.leg_radius_px *= 6.;
    a.flags = 2;
    r.render_offscreen(&target, &[a]).unwrap();
    let image = r.read_rgba(&target).unwrap();
    let mut mask: Vec<bool> = image.as_chunks::<4>().0.iter().map(|p| p[3] > 64).collect();
    let mut components = vec![];
    let mut stack = vec![];
    for origin in 0..mask.len() {
        if !mask[origin] {
            continue;
        }
        mask[origin] = false;
        stack.push(origin);
        let mut size = 0;
        while let Some(i) = stack.pop() {
            size += 1;
            let x = i % 512;
            let y = i / 512;
            for dy in -1isize..=1 {
                for dx in -1isize..=1 {
                    let nx = x as isize + dx;
                    let ny = y as isize + dy;
                    if !(0..512).contains(&nx) || !(0..512).contains(&ny) {
                        continue;
                    }
                    let j = ny as usize * 512 + nx as usize;
                    if mask[j] {
                        mask[j] = false;
                        stack.push(j);
                    }
                }
            }
        }
        if size > 20 {
            components.push(size);
        }
    }
    assert_eq!(
        components.len(),
        8,
        "six legs and two antennae must be independently visible: {components:?}"
    );
}
#[test]
fn subpixel_limbs_alone_survive_every_heading() {
    let p = profile();
    let mut r = pollster::block_on(Renderer::new_headless(&p.creatures[0])).unwrap();
    let target = r.offscreen(128, 128).unwrap();
    let mut a = ant();
    let scale = 96. / 254.;
    a.length_px *= scale;
    a.width_px *= scale;
    a.head_length_px *= scale;
    a.head_width_px *= scale;
    a.stride_px *= scale;
    a.leg_radius_px *= scale;
    a.flags = 2;
    let mut min = f64::INFINITY;
    let mut max: f64 = 0.;
    for degrees in (0..360).step_by(5) {
        a.heading_rad = (degrees as f32).to_radians();
        r.render_offscreen(&target, &[a]).unwrap();
        let rgba = r.read_rgba(&target).unwrap();
        let coverage: f64 = rgba
            .as_chunks::<4>()
            .0
            .iter()
            .map(|p| p[3] as f64 / 255.)
            .sum();
        min = min.min(coverage);
        max = max.max(coverage);
    }
    assert!(min > 2., "appendages lost at true size: {min}");
    assert!(
        max / min < 1.5,
        "limb-only rotational coverage unstable: {min}..{max}"
    );
}
#[test]
fn validation_scene_names_are_explicit_and_complete() {
    assert!(ValidationSceneId::parse("not-a-scene").is_err());
    let scenes = ValidationSceneId::all();
    assert_eq!(scenes.len(), 10);
    assert!(scenes.contains(&ValidationSceneId::Population1000));
}
#[test]
fn magnified_antennae_have_an_inward_elbow_not_two_straight_whiskers() {
    let p = profile();
    let mut r = pollster::block_on(Renderer::new_headless(&p.creatures[0])).unwrap();
    let target = r.offscreen(512, 512).unwrap();
    let mut a = ant();
    a.position_px = [256., 256.];
    for f in [
        &mut a.length_px,
        &mut a.width_px,
        &mut a.head_length_px,
        &mut a.head_width_px,
        &mut a.stride_px,
        &mut a.leg_radius_px,
    ] {
        *f *= 6.;
    }
    a.flags = 2;
    a.antenna = [0.64, 0.64];
    r.render_offscreen(&target, &[a]).unwrap();
    let pixels = r.read_rgba(&target).unwrap();
    let mut points = vec![];
    for (i, p) in pixels.as_chunks::<4>().0.iter().enumerate() {
        let x = (i % 512) as i32 - 256;
        let y = (i / 512) as i32 - 256;
        if x > 90 && y > 0 && p[3] > 64 {
            points.push((x, y));
        }
    }
    let end = points.iter().map(|p| p.0).max().unwrap();
    let outer = points.iter().map(|p| p.1).max().unwrap();
    let tip = points
        .iter()
        .filter(|p| p.0 > end - 4)
        .map(|p| p.1)
        .max()
        .unwrap();
    assert!(
        outer - tip >= 4,
        "antenna should bend toward front after its elbow: outer {outer}, tip {tip}"
    );
}

#[test]
fn destroyed_device_rejects_render_submission_instead_of_reporting_success() {
    let p = profile();
    let mut r = pollster::block_on(Renderer::new_headless(&p.creatures[0])).unwrap();
    let target = r.offscreen(128, 128).unwrap();
    r.render_offscreen(&target, &[ant()]).unwrap();
    r.device().destroy();
    // wgpu delivers the Destroyed callback after in-flight submissions complete.
    // This bounded wait is test-only; the utility always uses nonblocking polls.
    r.device()
        .poll(wgpu::PollType::Wait {
            submission_index: None,
            timeout: Some(std::time::Duration::from_secs(5)),
        })
        .unwrap();
    let _ = r.poll();
    assert!(
        r.check_health().is_err(),
        "destroyed device remained healthy"
    );
    assert!(r.render_offscreen(&target, &[ant()]).is_err());
}
