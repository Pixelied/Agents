use crate::{FixedClock, SimulationSnapshot, spatial::SpatialHash, trails::TrailField};
use creature_profile::{CreatureId, CreatureProfile, ProfileError, RangeF32, RuntimeProfileBundle};
use display_model::{DisplayId, DisplayTopology, Vec2Mm};
use glam::Vec2;
use serde::{Deserialize, Serialize};
use std::{
    sync::Arc,
    time::{Duration, Instant},
};
use thiserror::Error;

#[derive(Clone, Copy, Debug)]
pub struct SimulationConfig {
    pub tick_hz: u32,
    pub capacity: usize,
    pub seed: u64,
}
impl Default for SimulationConfig {
    fn default() -> Self {
        Self {
            tick_hz: 30,
            capacity: 5000,
            seed: 0xA17_2026,
        }
    }
}
#[derive(Debug, Error)]
pub enum SimulationError {
    #[error("invalid simulation configuration: {0}")]
    Invalid(&'static str),
    #[error(transparent)]
    Profile(#[from] ProfileError),
    #[error("nonfinite simulation state for creature {0}")]
    NonFinite(u64),
}
#[derive(Clone, Copy, Debug)]
pub struct CursorDisturbance {
    pub display: DisplayId,
    pub position_mm: Vec2Mm,
    pub velocity_mm_s: Vec2,
    pub strength: f32,
}
pub struct EnvironmentSnapshot<'a> {
    pub topology: &'a DisplayTopology,
    pub cursor: Option<CursorDisturbance>,
}
impl<'a> EnvironmentSnapshot<'a> {
    pub fn new(topology: &'a DisplayTopology) -> Self {
        Self {
            topology,
            cursor: None,
        }
    }
}
#[derive(Clone, Copy, Debug, Default, Serialize)]
pub struct SimulationTimings {
    pub total_ms: f64,
    pub behavior_ms: f64,
    pub spatial_ms: f64,
    pub trails_ms: f64,
}
#[derive(Clone, Copy, Debug, Default)]
pub struct AdvanceReport {
    pub ticks: u32,
    pub alpha: f32,
    pub dropped_ticks: u64,
    pub timings: SimulationTimings,
}
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[repr(u8)]
pub enum BehaviorState {
    #[default]
    Explore,
    Transit,
    Probe,
    Pause,
    EdgeFollow,
    TrailFollow,
    Encounter,
    Avoid,
    Disturbance,
    Groom,
}
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct AntTraits {
    pub length_mm: f32,
    pub width_mm: f32,
    pub head_length_mm: f32,
    pub head_width_mm: f32,
    pub stride_mm: f32,
    pub preferred_speed_mm_s: f32,
    pub turn_sign: f32,
    pub persistence_s: f32,
    pub pause_s: f32,
    pub morphology_seed: u32,
}
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct VisualCreatureState {
    pub id: CreatureId,
    pub kind_index: u16,
    pub display: DisplayId,
    pub previous_position_mm: Vec2Mm,
    pub position_mm: Vec2Mm,
    pub previous_heading_rad: f32,
    pub heading_rad: f32,
    pub body_length_mm: f32,
    pub body_width_mm: f32,
    pub head_length_mm: f32,
    pub head_width_mm: f32,
    pub stride_length_mm: f32,
    pub previous_gait_phase: f32,
    pub gait_phase: f32,
    pub gait_advance: f32,
    pub speed_mm_s: f32,
    pub speed_norm: f32,
    pub turn_amount: f32,
    pub pose_blend: f32,
    pub previous_antenna: [f32; 2],
    pub antenna: [f32; 2],
    pub morphology_seed: u32,
    pub behavior: BehaviorState,
}
#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct MotionCursor {
    pub track: usize,
    pub sample: usize,
    pub elapsed: f32,
}
/// Fixed-length, structure-of-arrays columns. Live slots are stable until exit;
/// the free list and active list are preallocated. Snapshot/export may allocate,
/// but warmed advance() and visual_states() never do.
pub(crate) struct Columns {
    pub ids: Vec<CreatureId>,
    pub displays: Vec<DisplayId>,
    pub positions: Vec<Vec2>,
    pub previous: Vec<Vec2>,
    pub velocities: Vec<Vec2>,
    pub headings: Vec<f32>,
    pub previous_headings: Vec<f32>,
    pub traits: Vec<AntTraits>,
    pub motion: Vec<MotionCursor>,
    pub rng: Vec<u64>,
    pub behavior: Vec<BehaviorState>,
    pub remaining: Vec<f32>,
    pub cooldown: Vec<f32>,
    pub partner: Vec<u64>,
    pub gait: Vec<f64>,
    pub previous_gait: Vec<f64>,
    pub turn: Vec<f32>,
    pub pose: Vec<f32>,
    pub antenna: Vec<[f32; 2]>,
    pub previous_antenna: Vec<[f32; 2]>,
    pub antenna_target: Vec<[f32; 2]>,
    pub antenna_timer: Vec<f32>,
    pub exiting: Vec<bool>,
    pub entered: Vec<bool>,
    pub was_low: Vec<bool>,
    pub active: Vec<usize>,
    pub free: Vec<usize>,
}
impl Columns {
    fn new(n: usize) -> Self {
        Self {
            ids: vec![CreatureId(0); n],
            displays: vec![DisplayId(0); n],
            positions: vec![Vec2::ZERO; n],
            previous: vec![Vec2::ZERO; n],
            velocities: vec![Vec2::ZERO; n],
            headings: vec![0.; n],
            previous_headings: vec![0.; n],
            traits: vec![AntTraits::default(); n],
            motion: vec![MotionCursor::default(); n],
            rng: vec![0; n],
            behavior: vec![BehaviorState::Explore; n],
            remaining: vec![0.; n],
            cooldown: vec![0.; n],
            partner: vec![0; n],
            gait: vec![0.; n],
            previous_gait: vec![0.; n],
            turn: vec![0.; n],
            pose: vec![0.; n],
            antenna: vec![[0.5, 0.6]; n],
            previous_antenna: vec![[0.5, 0.6]; n],
            antenna_target: vec![[0.5, 0.6]; n],
            antenna_timer: vec![0.; n],
            exiting: vec![false; n],
            entered: vec![false; n],
            was_low: vec![false; n],
            active: Vec::with_capacity(n),
            free: (0..n).rev().collect(),
        }
    }
    pub fn remove(&mut self, active_index: usize) {
        let i = self.active.swap_remove(active_index);
        self.free.push(i);
    }
}
#[derive(Clone, Copy)]
pub(crate) struct Biology {
    pub length: RangeF32,
    pub width: RangeF32,
    pub head: RangeF32,
    pub stride: RangeF32,
    pub speed: f32,
    pub up: f32,
    pub down: f32,
    pub speed_variation: f32,
    pub donor_median: f32,
    pub donor_p95: f32,
    pub low_motion: f32,
    pub turn: RangeF32,
    pub straight_threshold: f32,
    pub acceleration: RangeF32,
    pub persistence: RangeF32,
    pub pause: RangeF32,
    pub filter: f32,
    pub antenna_range: RangeF32,
    pub antenna_interval: RangeF32,
    pub antenna_filter: f32,
    pub encounter_radius: f32,
    pub encounter_probability: f32,
    pub edge_zone: f32,
    pub edge_exit: f32,
    pub trail_cell: f32,
    pub trail_decay: f32,
    pub trail_turn: f32,
    pub cursor_radius: f32,
}
impl Biology {
    fn new(p: &CreatureProfile) -> Result<Self, SimulationError> {
        let r = |k| p.range(k);
        let scalar = |k| r(k).map(|v| v.min);
        let mut turns: Vec<f32> = p
            .motion_tracks
            .iter()
            .flat_map(|t| t.samples.iter().map(|s| s.angular_velocity_rad_s.abs()))
            .collect();
        turns.sort_by(f32::total_cmp);
        let straight_threshold = turns.get(turns.len() / 2).copied().unwrap_or(0.);
        for key in [
            "donor_median_speed_mm_s",
            "velocity_filter_s",
            "antenna_filter_s",
            "stride_length_mm",
            "antenna_target_interval_s",
            "trail_cell_mm",
            "trail_decay_s",
        ] {
            if scalar(key)? <= 0. {
                return Err(SimulationError::Invalid("profile divisor must be positive"));
            }
        }
        Ok(Self {
            length: r("body_length_mm")?,
            width: r("head_width_mm")?,
            head: r("head_length_mm")?,
            stride: r("stride_length_mm")?,
            speed: scalar("preferred_speed_mm_s")?,
            up: scalar("climb_up_speed_mm_s")?,
            down: scalar("climb_down_speed_mm_s")?,
            speed_variation: scalar("speed_variation_fraction")?,
            donor_median: scalar("donor_median_speed_mm_s")?,
            donor_p95: scalar("donor_p95_speed_mm_s")?,
            low_motion: scalar("donor_low_motion_threshold_mm_s")?,
            turn: r("angular_velocity_rad_s")?,
            straight_threshold,
            acceleration: r("acceleration_mm_s2")?,
            persistence: r("direction_persistence_s")?,
            pause: r("pause_duration_s")?,
            filter: scalar("velocity_filter_s")?,
            antenna_range: r("antenna_sweep_rad")?,
            antenna_interval: r("antenna_target_interval_s")?,
            antenna_filter: scalar("antenna_filter_s")?,
            encounter_radius: scalar("encounter_radius_body_lengths")?,
            encounter_probability: scalar("encounter_response_probability")?,
            edge_zone: scalar("edge_zone_body_lengths")?,
            edge_exit: scalar("edge_exit_probability")?,
            trail_cell: scalar("trail_cell_mm")?,
            trail_decay: scalar("trail_decay_s")?,
            trail_turn: scalar("trail_turn_weight")?,
            cursor_radius: scalar("cursor_radius_mm")?,
        })
    }
}
/// SplitMix64 is used only for reproducible simulation choices, never security.
pub(crate) fn random_u64(state: &mut u64) -> u64 {
    *state = state.wrapping_add(0x9e3779b97f4a7c15);
    let mut z = *state;
    z = (z ^ (z >> 30)).wrapping_mul(0xbf58476d1ce4e5b9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94d049bb133111eb);
    z ^ (z >> 31)
}
pub(crate) fn unit(state: &mut u64) -> f32 {
    (random_u64(state) >> 40) as f32 / (1u32 << 24) as f32
}
pub(crate) fn sample(r: RangeF32, state: &mut u64) -> f32 {
    r.interpolate(unit(state))
}
pub fn wrapped_angle(x: f32) -> f32 {
    (x + std::f32::consts::PI).rem_euclid(std::f32::consts::TAU) - std::f32::consts::PI
}
pub(crate) fn sample_traits(b: Biology, rng: &mut u64) -> AntTraits {
    let length = sample(b.length, rng);
    let width = sample(b.width, rng);
    let head = sample(b.head, rng);
    AntTraits {
        length_mm: length,
        width_mm: width,
        head_length_mm: head,
        head_width_mm: width,
        stride_mm: sample(b.stride, rng),
        preferred_speed_mm_s: b.speed * (1. + (unit(rng) * 2. - 1.) * b.speed_variation),
        turn_sign: if unit(rng) < 0.5 { -1. } else { 1. },
        persistence_s: sample(b.persistence, rng),
        pause_s: sample(b.pause, rng),
        morphology_seed: random_u64(rng) as u32,
    }
}
pub struct Simulation {
    pub(crate) profiles: Arc<RuntimeProfileBundle>,
    pub(crate) ant_index: usize,
    pub(crate) biology: Biology,
    pub(crate) columns: Columns,
    pub(crate) clock: FixedClock,
    pub(crate) seed: u64,
    pub(crate) rng: u64,
    pub(crate) next_id: u64,
    pub(crate) target: u32,
    pub(crate) spawn_credit: f64,
    pub(crate) spawn_rate: f32,
    pub(crate) cursor_enabled: bool,
    pub(crate) activity: f32,
    pub(crate) trail_strength: f32,
    pub(crate) trail_lifetime: f32,
    pub(crate) spatial: SpatialHash,
    pub(crate) trails: Option<TrailField>,
    pub(crate) world_key: u64,
    pub(crate) step_index: u64,
    pub(crate) timings: SimulationTimings,
    visual: Vec<VisualCreatureState>,
}
impl Simulation {
    pub fn new(
        config: SimulationConfig,
        profiles: Arc<RuntimeProfileBundle>,
    ) -> Result<Self, SimulationError> {
        if config.capacity == 0 || config.capacity > 100_000 {
            return Err(SimulationError::Invalid("capacity must be 1..100000"));
        }
        let clock =
            FixedClock::new(config.tick_hz).ok_or(SimulationError::Invalid("tick frequency"))?;
        profiles.validate()?;
        let ant_index = profiles
            .creatures
            .iter()
            .position(|p| p.id.0 == "ant" && p.qualified)
            .ok_or(SimulationError::Invalid("qualified ant profile"))?;
        let biology = Biology::new(&profiles.creatures[ant_index])?;
        Ok(Self {
            profiles,
            ant_index,
            biology,
            columns: Columns::new(config.capacity),
            clock,
            seed: config.seed,
            rng: config.seed,
            next_id: 1,
            target: 0,
            spawn_credit: 0.,
            spawn_rate: 3.,
            cursor_enabled: false,
            activity: 1.,
            trail_strength: 0.65,
            trail_lifetime: 1.,
            spatial: SpatialHash::new(
                config.capacity,
                biology.length.max * biology.encounter_radius,
            ),
            trails: None,
            world_key: 0,
            step_index: 0,
            timings: SimulationTimings::default(),
            visual: Vec::with_capacity(config.capacity),
        })
    }
    pub fn len(&self) -> usize {
        self.columns.active.len()
    }
    pub fn is_empty(&self) -> bool {
        self.columns.active.is_empty()
    }
    pub fn capacity(&self) -> usize {
        self.columns.ids.len()
    }
    pub fn tick_count(&self) -> u64 {
        self.clock.tick
    }
    pub fn profiles(&self) -> &RuntimeProfileBundle {
        &self.profiles
    }
    pub fn set_paused(&mut self, p: bool) {
        self.clock.set_paused(p);
    }
    pub fn is_paused(&self) -> bool {
        self.clock.paused()
    }
    pub fn set_target_population(&mut self, target: u32) -> Result<(), SimulationError> {
        if target as usize > self.capacity() {
            return Err(SimulationError::Invalid("target exceeds capacity"));
        }
        self.target = target;
        Ok(())
    }
    pub fn configure_population(
        &mut self,
        rate: f32,
        activity: f32,
        trails: f32,
        lifetime: f32,
        cursor: bool,
    ) -> Result<(), SimulationError> {
        if !rate.is_finite()
            || !(0.1..=2000.).contains(&rate)
            || !activity.is_finite()
            || !(0.25..=2.).contains(&activity)
            || !trails.is_finite()
            || !(0.0..=2.).contains(&trails)
            || !lifetime.is_finite()
            || !(0.1..=10.).contains(&lifetime)
        {
            return Err(SimulationError::Invalid("population controls"));
        }
        self.spawn_rate = rate;
        self.activity = activity;
        self.trail_strength = trails;
        self.trail_lifetime = lifetime;
        self.cursor_enabled = cursor;
        if let Some(t) = &mut self.trails {
            t.set_decay(self.biology.trail_decay * lifetime);
        }
        Ok(())
    }
    pub fn advance(
        &mut self,
        dt: Duration,
        env: &EnvironmentSnapshot<'_>,
    ) -> Result<AdvanceReport, SimulationError> {
        let start = Instant::now();
        self.timings = SimulationTimings::default();
        let advance = self.clock.advance(dt);
        for _ in 0..advance.ticks {
            self.tick(env)?;
        }
        if advance.ticks > 0 {
            self.rebuild_visuals();
        }
        Ok(AdvanceReport {
            ticks: advance.ticks,
            alpha: advance.alpha,
            dropped_ticks: advance.dropped_ticks,
            timings: SimulationTimings {
                total_ms: start.elapsed().as_secs_f64() * 1000.,
                ..self.timings
            },
        })
    }
    pub fn visual_states(&self) -> &[VisualCreatureState] {
        &self.visual
    }
    pub fn traits_for(&self, id: CreatureId) -> Option<&AntTraits> {
        self.columns
            .active
            .iter()
            .find(|&&i| self.columns.ids[i] == id)
            .map(|&i| &self.columns.traits[i])
    }
    pub fn trails(&self) -> Option<&TrailField> {
        self.trails.as_ref()
    }
    pub fn spatial_cell_mm(&self) -> f32 {
        self.spatial.cell_size_mm()
    }
    pub fn snapshot(&self) -> SimulationSnapshot {
        SimulationSnapshot::capture(self)
    }
    pub fn signature(&self) -> String {
        self.snapshot().signature()
    }
    /// Explicit developer/validation operation. Ordinary startup never calls this.
    pub fn spawn_developer(
        &mut self,
        display: DisplayId,
        position: Vec2Mm,
        heading: f32,
    ) -> Result<CreatureId, SimulationError> {
        if !position.as_vec2().is_finite() || !heading.is_finite() {
            return Err(SimulationError::Invalid("developer spawn position"));
        }
        let id = self.spawn(display, position.as_vec2(), heading, true)?;
        self.rebuild_visuals();
        Ok(id)
    }
    /// Explicit developer batch operation; normal spawning still enters through physical edges.
    pub fn spawn_developer_batch(
        &mut self,
        spawns: &[(DisplayId, Vec2Mm, f32)],
    ) -> Result<(), SimulationError> {
        if spawns.len() > self.columns.free.len()
            || spawns
                .iter()
                .any(|(_, p, h)| !p.as_vec2().is_finite() || !h.is_finite())
        {
            return Err(SimulationError::Invalid(
                "developer batch capacity or position",
            ));
        }
        for &(display, position, heading) in spawns {
            self.spawn(display, position.as_vec2(), heading, true)?;
        }
        self.rebuild_visuals();
        Ok(())
    }
    pub(crate) fn spawn(
        &mut self,
        display: DisplayId,
        p: Vec2,
        heading: f32,
        entered: bool,
    ) -> Result<CreatureId, SimulationError> {
        let i = self
            .columns
            .free
            .pop()
            .ok_or(SimulationError::Invalid("capacity exhausted"))?;
        let c = &mut self.columns;
        let id = CreatureId(self.next_id);
        self.next_id += 1;
        let mut rng = random_u64(&mut self.rng);
        let traits = sample_traits(self.biology, &mut rng);
        let tracks = &self.profiles.creatures[self.ant_index].motion_tracks;
        let track = (random_u64(&mut rng) % tracks.len() as u64) as usize;
        let index = (random_u64(&mut rng) % tracks[track].samples.len() as u64) as usize;
        c.ids[i] = id;
        c.displays[i] = display;
        c.positions[i] = p;
        c.previous[i] = p;
        c.headings[i] = wrapped_angle(heading);
        c.previous_headings[i] = c.headings[i];
        c.traits[i] = traits;
        c.motion[i] = MotionCursor {
            track,
            sample: index,
            elapsed: 0.,
        };
        c.rng[i] = rng;
        c.velocities[i] = Vec2::ZERO;
        c.gait[i] = 0.;
        c.previous_gait[i] = 0.;
        c.turn[i] = 0.;
        c.pose[i] = 0.;
        c.remaining[i] = traits.persistence_s;
        c.cooldown[i] = 0.;
        c.partner[i] = 0;
        c.antenna[i] = [0.5, 0.6];
        c.previous_antenna[i] = c.antenna[i];
        c.antenna_target[i] = c.antenna[i];
        c.antenna_timer[i] = 0.;
        c.exiting[i] = false;
        c.entered[i] = entered;
        c.was_low[i] = false;
        c.behavior[i] = BehaviorState::Explore;
        c.active.push(i);
        Ok(id)
    }
    pub(crate) fn tick(&mut self, env: &EnvironmentSnapshot<'_>) -> Result<(), SimulationError> {
        crate::behavior::tick(self, env)
    }
    pub(crate) fn rebuild_visuals(&mut self) {
        self.visual.clear();
        let c = &self.columns;
        for &i in &c.active {
            let t = c.traits[i];
            let speed = c.velocities[i].length();
            self.visual.push(VisualCreatureState {
                id: c.ids[i],
                kind_index: self.ant_index as u16,
                display: c.displays[i],
                previous_position_mm: c.previous[i].into(),
                position_mm: c.positions[i].into(),
                previous_heading_rad: c.previous_headings[i],
                heading_rad: c.headings[i],
                body_length_mm: t.length_mm,
                body_width_mm: t.width_mm,
                head_length_mm: t.head_length_mm,
                head_width_mm: t.head_width_mm,
                stride_length_mm: t.stride_mm,
                previous_gait_phase: c.previous_gait[i].fract() as f32,
                gait_phase: c.gait[i].fract() as f32,
                gait_advance: (c.gait[i] - c.previous_gait[i]) as f32,
                speed_mm_s: speed,
                speed_norm: (speed
                    / (self.biology.donor_p95 / self.biology.donor_median
                        * t.preferred_speed_mm_s))
                    .clamp(0., 1.),
                turn_amount: c.turn[i],
                pose_blend: c.pose[i],
                previous_antenna: c.previous_antenna[i],
                antenna: c.antenna[i],
                morphology_seed: t.morphology_seed,
                behavior: c.behavior[i],
            });
        }
    }
}

#[cfg(test)]
mod statistical_tests {
    use super::*;
    #[test]
    fn ten_thousand_persistent_traits_respect_profile_ranges_and_quantiles() {
        let p = RuntimeProfileBundle::decode(include_bytes!(
            "../../../assets/creature-profiles/runtime-profiles.bin"
        ))
        .unwrap();
        let b = Biology::new(&p.creatures[0]).unwrap();
        let mut rng = 0x6172026;
        let mut lengths = Vec::new();
        let mut pauses = Vec::new();
        let mut runs = Vec::new();
        for _ in 0..10_000 {
            let t = sample_traits(b, &mut rng);
            assert!((b.length.min..=b.length.max).contains(&t.length_mm));
            assert!((b.stride.min..=b.stride.max).contains(&t.stride_mm));
            assert!((b.persistence.min..=b.persistence.max).contains(&t.persistence_s));
            assert!((b.pause.min..=b.pause.max).contains(&t.pause_s));
            lengths.push(t.length_mm);
            pauses.push(t.pause_s);
            runs.push(t.persistence_s);
        }
        for (mut values, r) in [
            (lengths, b.length),
            (pauses, b.pause),
            (runs, b.persistence),
        ] {
            values.sort_by(f32::total_cmp);
            for (index, q) in [(500, 0.05), (5000, 0.5), (9500, 0.95)] {
                assert!((values[index] - r.interpolate(q)).abs() < (r.max - r.min) * 0.025);
            }
        }
    }
}
