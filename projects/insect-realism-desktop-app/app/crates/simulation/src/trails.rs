//! Coarse normalized trail affinity, not a claim about pheromone concentration.
//! Lazy exponential decay is exact between accesses, with deterministic storage.
use display_model::{DisplayId, DisplayTopology};
use glam::Vec2;
#[derive(Clone, Copy, Debug, Default)]
pub struct TrailSample {
    pub intensity: f32,
    pub direction: Vec2,
}
#[derive(Clone, Copy, Default)]
struct Cell {
    intensity: f32,
    flow: Vec2,
    updated: f64,
}
struct Grid {
    id: DisplayId,
    width: usize,
    height: usize,
    size: Vec2,
    cells: Vec<Cell>,
}
pub struct TrailField {
    grids: Vec<Grid>,
    cell_mm: f32,
    decay_s: f32,
    time: f64,
}
impl TrailField {
    pub fn new(
        topology: &DisplayTopology,
        cell_mm: f32,
        decay_s: f32,
    ) -> Result<Self, &'static str> {
        if !cell_mm.is_finite() || cell_mm <= 0. || !decay_s.is_finite() || decay_s <= 0. {
            return Err("invalid trail grid parameters");
        }
        let mut grids = Vec::new();
        let mut total = 0usize;
        for d in topology
            .displays()
            .iter()
            .filter(|d| d.calibration.is_ready())
        {
            let size = d.size_mm();
            let width = (size.x / cell_mm).ceil() as usize;
            let height = (size.y / cell_mm).ceil() as usize;
            let n = width.checked_mul(height).ok_or("trail grid overflow")?;
            total = total.checked_add(n).ok_or("trail grid overflow")?;
            if total > 2_000_000 {
                return Err("trail memory budget exceeded");
            }
            grids.push(Grid {
                id: d.id,
                width,
                height,
                size,
                cells: vec![Cell::default(); n],
            });
        }
        Ok(Self {
            grids,
            cell_mm,
            decay_s,
            time: 0.,
        })
    }
    pub fn set_decay(&mut self, decay_s: f32) {
        if decay_s.is_finite() && decay_s > 0. && decay_s != self.decay_s {
            for g in &mut self.grids {
                for c in &mut g.cells {
                    c.intensity *= (-((self.time - c.updated) as f32) / self.decay_s).exp();
                    c.updated = self.time;
                }
            }
            self.decay_s = decay_s;
        }
    }
    pub fn sample(&self, id: DisplayId, p: Vec2) -> TrailSample {
        if !p.is_finite() {
            return TrailSample::default();
        }
        let Some(g) = self.grids.iter().find(|g| g.id == id) else {
            return TrailSample::default();
        };
        if p.x < 0. || p.y < 0. || p.x >= g.size.x || p.y >= g.size.y {
            return TrailSample::default();
        }
        let q = p / self.cell_mm - Vec2::splat(0.5);
        let x = q.x.floor() as isize;
        let y = q.y.floor() as isize;
        let tx = q.x - q.x.floor();
        let ty = q.y - q.y.floor();
        let mut intensity = 0.;
        let mut flow = Vec2::ZERO;
        for (dx, dy, w) in [
            (0, 0, (1. - tx) * (1. - ty)),
            (1, 0, tx * (1. - ty)),
            (0, 1, (1. - tx) * ty),
            (1, 1, tx * ty),
        ] {
            let xx = x + dx;
            let yy = y + dy;
            if xx < 0 || yy < 0 || xx as usize >= g.width || yy as usize >= g.height {
                continue;
            }
            let c = g.cells[yy as usize * g.width + xx as usize];
            let v = c.intensity * (-((self.time - c.updated) as f32) / self.decay_s).exp() * w;
            intensity += v;
            flow += c.flow * v;
        }
        TrailSample {
            intensity: intensity.clamp(0., 1.),
            direction: flow.normalize_or_zero(),
        }
    }
    pub fn deposit(&mut self, id: DisplayId, p: Vec2, direction: Vec2, amount: f32) -> bool {
        if !p.is_finite() || !direction.is_finite() || !amount.is_finite() || amount <= 0. {
            return false;
        }
        let Some(g) = self.grids.iter_mut().find(|g| g.id == id) else {
            return false;
        };
        if p.x < 0. || p.y < 0. || p.x >= g.size.x || p.y >= g.size.y {
            return false;
        }
        let x = (p.x / self.cell_mm) as usize;
        let y = (p.y / self.cell_mm) as usize;
        let c = &mut g.cells[y * g.width + x];
        let old = c.intensity * (-((self.time - c.updated) as f32) / self.decay_s).exp();
        let amount = amount.min(1.);
        c.flow = (c.flow * old + direction.normalize_or_zero() * amount).normalize_or_zero();
        c.intensity = (old + amount).min(1.);
        c.updated = self.time;
        true
    }
    pub fn decay_step(&mut self, dt: f32) {
        if dt.is_finite() && dt >= 0. {
            self.time += f64::from(dt);
        }
    }
    pub fn cell_size_mm(&self) -> f32 {
        self.cell_mm
    }
    pub fn cell_count(&self) -> usize {
        self.grids.iter().map(|g| g.cells.len()).sum()
    }
    /// Debug traversal; callbacks avoid allocating a per-frame vector.
    pub fn visit_cells(&self, id: DisplayId, mut f: impl FnMut(Vec2, f32)) {
        if let Some(g) = self.grids.iter().find(|g| g.id == id) {
            for (i, c) in g.cells.iter().enumerate() {
                let value = c.intensity * (-((self.time - c.updated) as f32) / self.decay_s).exp();
                if value > 0.001 {
                    f(
                        Vec2::new((i % g.width) as f32 + 0.5, (i / g.width) as f32 + 0.5)
                            * self.cell_mm,
                        value,
                    );
                }
            }
        }
    }
}
