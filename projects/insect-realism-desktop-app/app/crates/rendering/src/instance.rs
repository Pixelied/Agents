use crate::{Lod, RenderError, select_lod};
use creature_profile::{CreatureProfile, RangeF32};
use display_model::DisplaySurface;
use simulation::{VisualCreatureState, wrapped_angle};
use std::collections::HashMap;
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, bytemuck::Pod, bytemuck::Zeroable)]
pub struct CreatureRenderInstance {
    pub position_px: [f32; 2],
    pub heading_rad: f32,
    pub length_px: f32,
    pub width_px: f32,
    pub head_length_px: f32,
    pub head_width_px: f32,
    pub gait_phase: f32,
    pub antenna: [f32; 2],
    pub speed_norm: f32,
    pub turn_amount: f32,
    pub pose_blend: f32,
    pub stride_px: f32,
    pub leg_radius_px: f32,
    pub padding: f32,
    pub morphology_seed: u32,
    pub lod: u32,
    pub behavior: u32,
    pub flags: u32,
    pub material: [f32; 4],
}
impl CreatureRenderInstance {
    pub fn validate(&self) -> bool {
        [
            self.position_px[0],
            self.position_px[1],
            self.heading_rad,
            self.length_px,
            self.width_px,
            self.head_length_px,
            self.head_width_px,
            self.gait_phase,
            self.antenna[0],
            self.antenna[1],
            self.speed_norm,
            self.turn_amount,
            self.pose_blend,
            self.stride_px,
            self.leg_radius_px,
            self.padding,
            self.material[0],
            self.material[1],
            self.material[2],
            self.material[3],
        ]
        .iter()
        .all(|x| x.is_finite())
            && self.length_px > 0.
            && self.width_px > 0.
            && self.head_length_px > 0.
            && self.head_length_px < self.length_px
            && self.head_width_px > 0.
            && self.stride_px > 0.
            && self.leg_radius_px > 0.
            && self.lod <= 2
    }
}
pub struct InstanceBuilder {
    values: Vec<CreatureRenderInstance>,
    // Double-buffer active IDs: collisions are resolved, vanished IDs are removed,
    // and clear/swap retain reserved buckets rather than allocating per frame.
    previous_lods: HashMap<u64, Lod>,
    next_lods: HashMap<u64, Lod>,
    capacity: usize,
    leg_mm: RangeF32,
    debug_colors: bool,
    lod_counts: [u32; 3],
}
impl InstanceBuilder {
    pub fn new(capacity: usize, profile: &CreatureProfile) -> Result<Self, RenderError> {
        let leg_mm = profile
            .range("leg_thickness_mm")
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        if capacity == 0 || capacity > 100_000 || leg_mm.min <= 0. || leg_mm.max < leg_mm.min {
            return Err(RenderError::Invalid("instance capacity or limb thickness"));
        }
        Ok(Self {
            values: Vec::with_capacity(capacity),
            previous_lods: HashMap::with_capacity(capacity),
            next_lods: HashMap::with_capacity(capacity),
            capacity,
            leg_mm,
            debug_colors: false,
            lod_counts: [0; 3],
        })
    }
    pub fn set_debug_colors(&mut self, enabled: bool) {
        self.debug_colors = enabled;
    }
    pub fn lod_counts(&self) -> [u32; 3] {
        self.lod_counts
    }
    pub fn build(
        &mut self,
        states: &[VisualCreatureState],
        display: &DisplaySurface,
        alpha: f32,
        scale: f32,
    ) -> Result<&[CreatureRenderInstance], RenderError> {
        self.values.clear();
        self.next_lods.clear();
        self.lod_counts = [0; 3];
        display
            .validate()
            .map_err(|_| RenderError::Invalid("display"))?;
        if !display.calibration.is_ready() {
            self.previous_lods.clear();
            return Ok(&self.values);
        }
        if !alpha.is_finite() || !scale.is_finite() || scale <= 0. || scale > 32. {
            return Err(RenderError::Invalid(
                "interpolation or deliberate creature scale",
            ));
        }
        let alpha = alpha.clamp(0., 1.);
        let pixels_per_mm = display.calibration.mm_per_physical_px.recip();
        for (count, v) in states
            .iter()
            .filter(|v| v.display == display.id)
            .enumerate()
        {
            if count >= self.capacity {
                return Err(RenderError::Invalid(
                    "instance population exceeds reserved capacity",
                ));
            }
            let p = v
                .previous_position_mm
                .as_vec2()
                .lerp(v.position_mm.as_vec2(), alpha)
                * pixels_per_mm;
            let length = v.body_length_mm * scale * pixels_per_mm;
            let lod = select_lod(length, self.previous_lods.get(&v.id.0).copied());
            self.next_lods.insert(v.id.0, lod);
            let hash = v.morphology_seed.wrapping_mul(0x9e3779b9).rotate_left(13);
            let u = (hash >> 8) as f32 / (1u32 << 24) as f32;
            let tint = 0.01 + u * 0.008;
            let material = if self.debug_colors {
                state_color(v.behavior as usize)
            } else {
                [tint, tint * 0.77, tint * 0.58, 0.98]
            };
            let instance = CreatureRenderInstance {
                position_px: p.to_array(),
                heading_rad: wrapped_angle(
                    v.previous_heading_rad
                        + wrapped_angle(v.heading_rad - v.previous_heading_rad) * alpha,
                ),
                length_px: length,
                width_px: v.body_width_mm * scale * pixels_per_mm,
                head_length_px: v.head_length_mm * scale * pixels_per_mm,
                head_width_px: v.head_width_mm * scale * pixels_per_mm,
                gait_phase: (v.previous_gait_phase + v.gait_advance * alpha).rem_euclid(1.),
                antenna: [
                    v.previous_antenna[0] + (v.antenna[0] - v.previous_antenna[0]) * alpha,
                    v.previous_antenna[1] + (v.antenna[1] - v.previous_antenna[1]) * alpha,
                ],
                speed_norm: v.speed_norm,
                turn_amount: v.turn_amount,
                pose_blend: v.pose_blend,
                stride_px: v.stride_length_mm * scale * pixels_per_mm,
                leg_radius_px: self.leg_mm.interpolate(u) * scale * pixels_per_mm * 0.5,
                padding: 0.,
                morphology_seed: v.morphology_seed,
                lod: lod as u32,
                behavior: v.behavior as u32,
                flags: 0,
                material,
            };
            if !instance.validate() {
                return Err(RenderError::Invalid(
                    "nonfinite or invalid creature instance",
                ));
            }
            let extent = length * 1.4;
            if p.x + extent < 0.
                || p.y + extent < 0.
                || p.x - extent > display.pixels.width as f32
                || p.y - extent > display.pixels.height as f32
            {
                continue;
            }
            self.values.push(instance);
            self.lod_counts[lod as usize] += 1;
        }
        std::mem::swap(&mut self.previous_lods, &mut self.next_lods);
        Ok(&self.values)
    }
}
fn state_color(i: usize) -> [f32; 4] {
    const C: [[f32; 4]; 10] = [
        [0.2, 0.7, 0.3, 1.],
        [0.1, 0.4, 0.9, 1.],
        [0.7, 0.4, 0.1, 1.],
        [0.5, 0.5, 0.5, 1.],
        [0.8, 0.3, 0.7, 1.],
        [0.1, 0.7, 0.7, 1.],
        [0.9, 0.2, 0.3, 1.],
        [0.8, 0.7, 0.1, 1.],
        [0.9, 0.4, 0.1, 1.],
        [0.4, 0.3, 0.6, 1.],
    ];
    C[i.min(9)]
}
