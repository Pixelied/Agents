use rendering::*;
use std::time::Duration;
use wgpu::{Backend, CompositeAlphaMode as A, PresentMode as P};
#[test]
fn opaque_only_overlay_is_rejected() {
    for backend in [Backend::Vulkan, Backend::Metal, Backend::Dx12] {
        assert!(transparent_alpha(&[A::Opaque, A::Auto], backend).is_err());
    }
}
#[test]
fn premultiplied_alpha_is_preferred() {
    assert_eq!(
        transparent_alpha(&[A::Opaque, A::PreMultiplied], Backend::Dx12).unwrap(),
        A::PreMultiplied
    );
}
#[test]
fn metal_label_quirk_is_not_applied_to_other_backends() {
    assert_eq!(
        transparent_alpha(&[A::Opaque, A::PostMultiplied], Backend::Metal).unwrap(),
        A::PostMultiplied
    );
    assert!(transparent_alpha(&[A::PostMultiplied], Backend::Vulkan).is_err());
}
#[test]
fn presentation_is_scheduled_without_unnecessary_cross_monitor_waits() {
    assert_eq!(overlay_present_mode(&[P::Fifo, P::Mailbox]), P::Mailbox);
    assert_eq!(overlay_present_mode(&[P::Fifo, P::Immediate]), P::Immediate);
    assert_eq!(overlay_present_mode(&[P::Fifo]), P::Fifo);
}
#[test]
fn displays_have_independent_refresh_cadences() {
    for hz in [60., 120., 144., 165., 240.] {
        let mut cadence = FrameCadence::new(hz).unwrap();
        let mut count = 0;
        for ms in 0..1000 {
            if cadence.take_due(Duration::from_millis(ms)) {
                count += 1;
            }
        }
        assert_eq!(count, hz as u32);
    }
}
#[test]
fn stalled_render_clock_does_not_emit_a_catchup_burst() {
    let mut c = FrameCadence::new(144.).unwrap();
    assert!(c.take_due(Duration::from_secs(3600)));
    assert!(!c.take_due(Duration::from_secs(3600)));
    assert!(c.next_deadline() > Duration::from_secs(3600));
    assert!(FrameCadence::new(f32::NAN).is_err());
}
