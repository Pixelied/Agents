use crate::{
    Simulation, SimulationError,
    state::{random_u64, unit},
    trails::TrailField,
};
use display_model::{DisplayId, DisplayTopology};
use glam::Vec2;
fn topology_key(t: &DisplayTopology) -> u64 {
    let mut key = 0xcbf29ce484222325u64;
    for d in t.displays() {
        for v in [
            d.id.0,
            d.pixels.width as u64,
            d.pixels.height as u64,
            d.calibration.mm_per_physical_px.to_bits() as u64,
            u64::from(d.calibration.is_ready()),
            d.desktop_bounds.x.to_bits(),
            d.desktop_bounds.y.to_bits(),
            d.desktop_bounds.width.to_bits(),
            d.desktop_bounds.height.to_bits(),
        ] {
            key = (key ^ v).wrapping_mul(0x100000001b3);
        }
    }
    key ^ u64::from(t.continuous())
}
impl Simulation {
    /// Geometry changes are an explicitly allocation-permitted lifecycle boundary.
    /// Removed displays discard their invisible residents rather than teleporting
    /// them into the middle of another monitor. Normal entry replaces them later.
    pub fn reconfigure_topology(&mut self, t: &DisplayTopology) -> Result<(), SimulationError> {
        let key = topology_key(t);
        if self.world_key == key && self.trails.is_some() {
            return Ok(());
        }
        if t.displays().len() > 64 {
            return Err(SimulationError::Invalid("maximum 64 displays"));
        }
        let replacement = TrailField::new(
            t,
            self.biology.trail_cell,
            self.biology.trail_decay * self.trail_lifetime,
        )
        .map_err(SimulationError::Invalid)?;
        self.trails = Some(replacement);
        self.world_key = key;
        let mut a = 0;
        while a < self.columns.active.len() {
            let i = self.columns.active[a];
            if t.display(self.columns.displays[i])
                .is_none_or(|d| !d.calibration.is_ready())
            {
                self.columns.remove(a);
            } else {
                a += 1;
            }
        }
        self.rebuild_visuals();
        Ok(())
    }
    pub(crate) fn update_population(
        &mut self,
        t: &DisplayTopology,
        dt: f32,
    ) -> Result<(), SimulationError> {
        let mut eligible = [DisplayId(0); 64];
        let mut count = 0usize;
        for d in t.displays().iter().filter(|d| d.calibration.is_ready()) {
            eligible[count] = d.id;
            count += 1;
        }
        if count == 0 {
            return Ok(());
        }
        let excess = self.len().saturating_sub(self.target as usize);
        let mut marked = self
            .columns
            .active
            .iter()
            .filter(|&&i| self.columns.exiting[i])
            .count();
        if marked < excess {
            for &i in &self.columns.active {
                if !self.columns.exiting[i] {
                    self.columns.exiting[i] = true;
                    marked += 1;
                    if marked >= excess {
                        break;
                    }
                }
            }
        }
        if self.len() >= self.target as usize {
            self.spawn_credit = 0.;
            return Ok(());
        }
        self.spawn_credit = (self.spawn_credit + f64::from(dt * self.spawn_rate) * count as f64)
            .min(f64::from(self.target));
        let mut populations = [0usize; 64];
        for &i in &self.columns.active {
            if let Some(k) = eligible[..count]
                .iter()
                .position(|&id| id == self.columns.displays[i])
            {
                populations[k] += 1;
            }
        }
        while self.spawn_credit >= 1. && self.len() < (self.target as usize) {
            // Rotate tie-breaking from the seeded stream; keep independent monitors balanced.
            let first = (random_u64(&mut self.rng) % count as u64) as usize;
            let mut chosen = first;
            for j in 1..count {
                let k = (first + j) % count;
                if populations[k] < populations[chosen] {
                    chosen = k;
                }
            }
            let d = t
                .display(eligible[chosen])
                .expect("eligible monitor exists");
            let size = d.size_mm();
            let along = unit(&mut self.rng);
            let side = random_u64(&mut self.rng) % 4;
            let offset = self.biology.length.max * 0.5;
            let (p, h) = match side {
                0 => (Vec2::new(-offset, along * size.y), 0.),
                1 => (
                    Vec2::new(size.x + offset, along * size.y),
                    std::f32::consts::PI,
                ),
                2 => (
                    Vec2::new(along * size.x, -offset),
                    std::f32::consts::FRAC_PI_2,
                ),
                _ => (
                    Vec2::new(along * size.x, size.y + offset),
                    -std::f32::consts::FRAC_PI_2,
                ),
            };
            self.spawn(d.id, p, h, false)?;
            populations[chosen] += 1;
            self.spawn_credit -= 1.;
        }
        Ok(())
    }
}
