use crate::{
    BehaviorState, EnvironmentSnapshot, Simulation, SimulationError,
    locomotion::{antennae, integrate, reference_speed},
    state::{sample, unit},
    wrapped_angle,
};
use display_model::Edge;
use glam::Vec2;
use std::time::Instant;
const NEIGHBOR_CANDIDATE_BUDGET: usize = 128;
/// All decisions see a common pre-integration spatial snapshot. Decisions are
/// staggered by stable creature id and fixed tick, never rendering frame number.
pub(crate) fn tick(
    s: &mut Simulation,
    env: &EnvironmentSnapshot<'_>,
) -> Result<(), SimulationError> {
    s.reconfigure_topology(env.topology)?;
    let dt = s.clock.dt();
    s.step_index += 1;
    s.update_population(env.topology, dt)?;
    let begin = Instant::now();
    s.spatial
        .rebuild(&s.columns.positions, &s.columns.displays, &s.columns.active);
    s.timings.spatial_ms += begin.elapsed().as_secs_f64() * 1000.;
    let begin = Instant::now();
    s.trails
        .as_mut()
        .expect("initialized trails")
        .decay_step(dt);
    s.timings.trails_ms += begin.elapsed().as_secs_f64() * 1000.;
    let begin = Instant::now();
    let b = s.biology;
    let tracks = &s.profiles.creatures[s.ant_index].motion_tracks;
    let c = &mut s.columns;
    for &i in &c.active {
        c.previous[i] = c.positions[i];
        c.previous_headings[i] = c.headings[i];
        c.previous_gait[i] = c.gait[i];
        c.previous_antenna[i] = c.antenna[i];
    }
    for a in 0..c.active.len() {
        let i = c.active[a];
        let Some(display) = env.topology.display(c.displays[i]) else {
            continue;
        };
        let size = display.size_mm();
        let p = c.previous[i];
        let head = c.headings[i];
        let forward = Vec2::from_angle(head);
        let length = c.traits[i].length_mm;
        c.remaining[i] -= dt;
        c.cooldown[i] = (c.cooldown[i] - dt).max(0.);
        let cursor = &mut c.motion[i];
        let track = &tracks[cursor.track];
        cursor.elapsed += dt;
        while cursor.elapsed >= track.samples[cursor.sample].dt_s {
            cursor.elapsed -= track.samples[cursor.sample].dt_s;
            cursor.sample = (cursor.sample + 1) % track.samples.len();
        }
        let measured = track.samples[cursor.sample];
        let low = measured.speed_mm_s <= b.low_motion;
        if low
            && !c.was_low[i]
            && !matches!(
                c.behavior[i],
                BehaviorState::Encounter | BehaviorState::Disturbance
            )
        {
            c.behavior[i] = BehaviorState::Pause;
            c.remaining[i] = sample(b.pause, &mut c.rng[i]) / s.activity;
        }
        c.was_low[i] = low;
        if c.remaining[i] <= 0. {
            c.behavior[i] = if low {
                BehaviorState::Probe
            } else if measured.angular_velocity_rad_s.abs() <= b.straight_threshold {
                BehaviorState::Transit
            } else {
                BehaviorState::Explore
            };
            c.remaining[i] = sample(b.persistence, &mut c.rng[i]);
        }
        let reference = reference_speed(b, head, c.traits[i].preferred_speed_mm_s);
        let ratio = reference / b.donor_median;
        let mut speed = if low
            || matches!(
                c.behavior[i],
                BehaviorState::Pause | BehaviorState::Encounter
            ) {
            0.
        } else {
            measured.speed_mm_s.min(b.donor_p95) * ratio
        };
        let mut omega = measured.angular_velocity_rad_s * c.traits[i].turn_sign;
        let mut antenna_angle = None;
        let near = s.spatial.nearest(
            c.displays[i],
            p,
            length * b.encounter_radius,
            i,
            NEIGHBOR_CANDIDATE_BUDGET,
        );
        if let Some((other, q)) = near {
            let delta = q - p;
            antenna_angle = Some(wrapped_angle(delta.y.atan2(delta.x) - head));
            if (s.step_index + c.ids[i].0).is_multiple_of(3)
                && c.partner[i] != c.ids[other].0
                && c.cooldown[i] <= 0.
            {
                c.partner[i] = c.ids[other].0;
                c.cooldown[i] = c.traits[i].persistence_s;
                if unit(&mut c.rng[i]) < b.encounter_probability {
                    if forward.dot(Vec2::from_angle(c.previous_headings[other])) < 0. {
                        c.behavior[i] = BehaviorState::Encounter;
                        c.remaining[i] = sample(b.pause, &mut c.rng[i]);
                        speed = 0.;
                    } else if delta.length() < length * 0.5 {
                        c.behavior[i] = BehaviorState::Avoid;
                        c.remaining[i] = c.traits[i].persistence_s;
                    } else {
                        c.behavior[i] = BehaviorState::TrailFollow;
                        c.remaining[i] = c.traits[i].persistence_s;
                    }
                }
            }
            if c.behavior[i] == BehaviorState::Avoid {
                let away = (-delta).normalize_or_zero();
                omega += wrapped_angle(away.y.atan2(away.x) - head) / c.traits[i].persistence_s;
            }
        } else {
            c.partner[i] = 0;
        }
        if !matches!(
            c.behavior[i],
            BehaviorState::Pause | BehaviorState::Encounter | BehaviorState::Avoid
        ) && s.trail_strength > 0.
        {
            let trails = s.trails.as_ref().expect("initialized trails");
            let antenna_offset = Vec2::new(-forward.y, forward.x) * c.traits[i].head_width_mm;
            let left = trails.sample(c.displays[i], p + forward * length - antenna_offset);
            let right = trails.sample(c.displays[i], p + forward * length + antenna_offset);
            let center = trails.sample(c.displays[i], p + forward * length);
            let strength = ((left.intensity + right.intensity + center.intensity) / 3.
                * s.trail_strength
                * b.trail_turn)
                .clamp(0., 1.);
            if strength > 0.001 {
                let directional = if center.direction.length_squared() > 0. {
                    wrapped_angle(center.direction.y.atan2(center.direction.x) - head)
                        / c.traits[i].persistence_s
                } else {
                    0.
                };
                let imbalance = (right.intensity - left.intensity) * b.turn.max;
                omega = omega * (1. - strength) + (directional + imbalance) * strength;
                c.behavior[i] = BehaviorState::TrailFollow;
            }
        }
        let boundaries = [
            (p.x, Edge::Left, Vec2::X, p.y),
            (size.x - p.x, Edge::Right, -Vec2::X, p.y),
            (p.y, Edge::Top, Vec2::Y, p.x),
            (size.y - p.y, Edge::Bottom, -Vec2::Y, p.x),
        ];
        let edge = boundaries
            .iter()
            .filter(|(_, edge, _, along)| !env.topology.connected_at(c.displays[i], *edge, *along))
            .min_by(|a, b| a.0.total_cmp(&b.0));
        if p.x >= 0. && p.y >= 0. && p.x < size.x && p.y < size.y {
            c.entered[i] = true;
        }
        if !c.entered[i] {
            omega = 0.;
        } // complete edge entry before resuming a donor turn
        if let Some(&(distance, _, normal, _)) = edge {
            if c.exiting[i] {
                let outward = -normal;
                omega = wrapped_angle(outward.y.atan2(outward.x) - head) / b.filter;
                speed = reference;
            } else if c.entered[i]
                && distance < length * b.edge_zone
                && forward.dot(normal) < 0.25
                && !matches!(
                    c.behavior[i],
                    BehaviorState::Pause | BehaviorState::Encounter
                )
            {
                if c.behavior[i] != BehaviorState::EdgeFollow {
                    c.behavior[i] = BehaviorState::EdgeFollow;
                    c.remaining[i] = c.traits[i].persistence_s;
                    if unit(&mut c.rng[i]) < b.edge_exit {
                        c.exiting[i] = true;
                    }
                }
                if !c.exiting[i] {
                    let mut tangent = Vec2::new(-normal.y, normal.x);
                    if tangent.dot(forward) < 0. {
                        tangent = -tangent;
                    }
                    let inward = ((length - distance) / (length * b.edge_zone)).clamp(0.1, 1.);
                    let desired = (tangent + normal * inward).normalize();
                    omega = wrapped_angle(desired.y.atan2(desired.x) - head) / b.filter;
                }
            }
        }
        if s.cursor_enabled
            && let Some(cursor) = env.cursor
            && cursor.display == c.displays[i]
            && cursor.position_mm.as_vec2().is_finite()
            && cursor.velocity_mm_s.is_finite()
            && cursor.strength.is_finite()
            && cursor.strength > 0.
            && cursor.velocity_mm_s.length() > reference
            && p.distance_squared(cursor.position_mm.as_vec2()) < b.cursor_radius * b.cursor_radius
        {
            let away = (p - cursor.position_mm.as_vec2()).normalize_or(forward);
            omega = wrapped_angle(away.y.atan2(away.x) - head) / b.filter;
            speed = b.donor_p95 * ratio;
            c.behavior[i] = BehaviorState::Disturbance;
            c.remaining[i] = c.traits[i].pause_s;
        }
        integrate(c, i, b, speed, omega, dt);
        antennae(c, i, b, dt, antenna_angle);
        if !c.positions[i].is_finite()
            || !c.velocities[i].is_finite()
            || !c.headings[i].is_finite()
            || !c.gait[i].is_finite()
        {
            return Err(SimulationError::NonFinite(c.ids[i].0));
        }
    }
    let mut a = 0;
    while a < c.active.len() {
        let i = c.active[a];
        let d = c.displays[i];
        let size = env
            .topology
            .display(d)
            .expect("validated display")
            .size_mm();
        if let Some(crossing) = env.topology.crossing(d, c.positions[i], c.velocities[i]) {
            c.displays[i] = crossing.display;
            c.positions[i] = crossing.position_mm;
            c.velocities[i] = crossing.velocity_mm_s;
            c.previous[i] = crossing.position_mm - crossing.velocity_mm_s * dt;
            c.entered[i] = true;
        } else if c.entered[i]
            && (c.positions[i].x < -c.traits[i].length_mm
                || c.positions[i].y < -c.traits[i].length_mm
                || c.positions[i].x > size.x + c.traits[i].length_mm
                || c.positions[i].y > size.y + c.traits[i].length_mm)
        {
            c.remove(a);
            continue;
        }
        a += 1;
    }
    s.timings.behavior_ms += begin.elapsed().as_secs_f64() * 1000.;
    let begin = Instant::now();
    let trails = s.trails.as_mut().expect("initialized trails");
    for &i in &c.active {
        if c.velocities[i].length_squared() > 0. && c.entered[i] {
            trails.deposit(
                c.displays[i],
                c.positions[i],
                c.velocities[i],
                dt / b.trail_decay * s.trail_strength,
            );
        }
    }
    s.timings.trails_ms += begin.elapsed().as_secs_f64() * 1000.;
    Ok(())
}
