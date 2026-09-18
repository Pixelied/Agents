//! Reusable chained spatial hash. Hash collisions are checked against exact
//! cell, display, and Euclidean radius, so monitors never contaminate each other.
use display_model::DisplayId;
use glam::Vec2;
const NONE: usize = usize::MAX;
pub struct SpatialHash {
    cell: f32,
    heads: Vec<usize>,
    next: Vec<usize>,
    positions: Vec<Vec2>,
    displays: Vec<DisplayId>,
    cells: Vec<(i32, i32)>,
    occupied: Vec<usize>,
}
impl SpatialHash {
    pub fn new(capacity: usize, cell: f32) -> Self {
        assert!(capacity > 0 && capacity <= 100_000 && cell.is_finite() && cell > 0.);
        Self {
            cell,
            heads: vec![NONE; (capacity * 2).next_power_of_two()],
            next: vec![NONE; capacity],
            positions: vec![Vec2::ZERO; capacity],
            displays: vec![DisplayId(0); capacity],
            cells: vec![(0, 0); capacity],
            occupied: Vec::with_capacity(capacity),
        }
    }
    pub fn cell_size_mm(&self) -> f32 {
        self.cell
    }
    fn cell_at(&self, p: Vec2) -> (i32, i32) {
        (
            (p.x / self.cell).floor() as i32,
            (p.y / self.cell).floor() as i32,
        )
    }
    fn hash(&self, d: DisplayId, x: i32, y: i32) -> usize {
        let mut h = d.0.wrapping_mul(0x9e3779b97f4a7c15)
            ^ (x as u32 as u64).wrapping_mul(0x85ebca6b)
            ^ (y as u32 as u64).wrapping_mul(0xc2b2ae35);
        h ^= h >> 33;
        (h as usize) & (self.heads.len() - 1)
    }
    pub fn rebuild(&mut self, positions: &[Vec2], displays: &[DisplayId], active: &[usize]) {
        self.heads.fill(NONE);
        self.occupied.clear();
        for &i in active {
            if i >= self.next.len()
                || i >= positions.len()
                || i >= displays.len()
                || !positions[i].is_finite()
            {
                continue;
            }
            let p = positions[i];
            let d = displays[i];
            let (x, y) = self.cell_at(p);
            let h = self.hash(d, x, y);
            self.positions[i] = p;
            self.displays[i] = d;
            self.cells[i] = (x, y);
            self.next[i] = self.heads[h];
            self.heads[h] = i;
            self.occupied.push(i);
        }
    }
    /// Returns visited candidates. Production callers use a finite candidate
    /// budget, preventing a pathological coincident population from becoming an
    /// all-pairs workload. Unlimited queries remain exact for validation/tools.
    pub fn visit(
        &self,
        d: DisplayId,
        p: Vec2,
        r: f32,
        budget: usize,
        mut f: impl FnMut(usize, Vec2),
    ) -> usize {
        if !p.is_finite() || !r.is_finite() || r < 0. || budget == 0 {
            return 0;
        }
        let r2 = r * r;
        let mut visited = 0;
        if r / self.cell > 64. {
            for &i in &self.occupied {
                if visited >= budget {
                    break;
                }
                visited += 1;
                if self.displays[i] == d && self.positions[i].distance_squared(p) <= r2 {
                    f(i, self.positions[i]);
                }
            }
            return visited;
        }
        let (lx, ly) = self.cell_at(p - Vec2::splat(r));
        let (hx, hy) = self.cell_at(p + Vec2::splat(r));
        for y in ly..=hy {
            for x in lx..=hx {
                let mut i = self.heads[self.hash(d, x, y)];
                while i != NONE {
                    if visited >= budget {
                        return visited;
                    }
                    visited += 1;
                    if self.displays[i] == d
                        && self.cells[i] == (x, y)
                        && self.positions[i].distance_squared(p) <= r2
                    {
                        f(i, self.positions[i]);
                    }
                    i = self.next[i];
                }
            }
        }
        visited
    }
    pub fn neighbors(&self, d: DisplayId, p: Vec2, r: f32, out: &mut Vec<usize>) {
        out.clear();
        self.visit(d, p, r, usize::MAX, |i, _| out.push(i));
    }
    pub fn nearest(
        &self,
        d: DisplayId,
        p: Vec2,
        r: f32,
        exclude: usize,
        budget: usize,
    ) -> Option<(usize, Vec2)> {
        let mut answer = None;
        let mut best = f32::INFINITY;
        self.visit(d, p, r, budget, |i, q| {
            let distance = p.distance_squared(q);
            if i != exclude
                && (distance < best || (distance == best && answer.is_none_or(|(j, _)| i < j)))
            {
                best = distance;
                answer = Some((i, q));
            }
        });
        answer
    }
}
