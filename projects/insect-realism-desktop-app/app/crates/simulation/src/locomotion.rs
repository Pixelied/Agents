use crate::{
    BehaviorState,
    state::{Biology, Columns, sample, unit},
    wrapped_angle,
};
use glam::Vec2;
/// Heading-weighted interpolation of reported substrate/direction means is an
/// engineering transfer, not a new measured target-species speed distribution.
pub(crate) fn reference_speed(b: Biology, heading: f32, individual: f32) -> f32 {
    let vertical = heading.sin();
    let directional = if vertical < 0. { b.up } else { b.down };
    (b.speed + (directional - b.speed) * vertical.abs()) * individual / b.speed
}
pub(crate) fn integrate(
    c: &mut Columns,
    i: usize,
    b: Biology,
    target_speed: f32,
    target_turn: f32,
    dt: f32,
) {
    let blend = 1. - (-dt / b.filter).exp();
    c.turn[i] += (target_turn.clamp(b.turn.min, b.turn.max) - c.turn[i]) * blend;
    // No walking turn while genuinely stationary; antennae can still investigate.
    let previous_speed = c.velocities[i].length();
    let ratio =
        reference_speed(b, c.headings[i], c.traits[i].preferred_speed_mm_s) / b.donor_median;
    let delta = ((target_speed - previous_speed) * blend).clamp(
        b.acceleration.min * ratio * dt,
        b.acceleration.max * ratio * dt,
    );
    let mut speed = (previous_speed + delta).max(0.);
    if target_speed == 0. && speed < 0.0001 {
        speed = 0.;
    }
    if speed > 0.0001 {
        c.headings[i] = wrapped_angle(c.headings[i] + c.turn[i] * dt);
    }
    c.velocities[i] = Vec2::from_angle(c.headings[i]) * speed;
    c.positions[i] += c.velocities[i] * dt;
    c.gait[i] += f64::from(speed * dt / c.traits[i].stride_mm);
}
pub(crate) fn antennae(
    c: &mut Columns,
    i: usize,
    b: Biology,
    dt: f32,
    encounter_angle: Option<f32>,
) {
    c.antenna_timer[i] -= dt;
    if c.antenna_timer[i] <= 0. {
        c.antenna_timer[i] = sample(b.antenna_interval, &mut c.rng[i]);
        let left = sample(b.antenna_range, &mut c.rng[i]);
        let opposite = b.antenna_range.min + b.antenna_range.max - left;
        // Strong but imperfect anti-correlation: engineering reconstruction of the
        // observed donor mechanism; amplitude/timing priors retain profile provenance.
        let right = opposite * 0.8 + sample(b.antenna_range, &mut c.rng[i]) * 0.2;
        let forward = if c.behavior[i] == BehaviorState::Transit {
            0.55
        } else {
            1.
        };
        c.antenna_target[i] = [
            b.antenna_range.min + (left - b.antenna_range.min) * forward,
            b.antenna_range.min + (right - b.antenna_range.min) * forward,
        ];
        if let Some(angle) = encounter_angle {
            c.antenna_target[i] = [
                (0.45 + angle).clamp(b.antenna_range.min, b.antenna_range.max),
                (0.45 - angle).clamp(b.antenna_range.min, b.antenna_range.max),
            ];
        }
        // Tiny persistent asymmetry prevents identical extrema, not per-frame noise.
        if c.antenna_target[i][0] == c.antenna_target[i][1] {
            c.antenna_target[i][1] =
                (c.antenna_target[i][1] + unit(&mut c.rng[i]) * 0.01).min(b.antenna_range.max);
        }
    }
    let blend = 1. - (-dt / b.antenna_filter).exp();
    for a in 0..2 {
        c.antenna[i][a] += (c.antenna_target[i][a] - c.antenna[i][a]) * blend;
    }
    let pose = match c.behavior[i] {
        BehaviorState::Encounter => 1.,
        BehaviorState::Probe | BehaviorState::EdgeFollow => 0.6,
        BehaviorState::Pause => 0.3,
        _ => 0.,
    };
    c.pose[i] += (pose - c.pose[i]) * blend;
}
