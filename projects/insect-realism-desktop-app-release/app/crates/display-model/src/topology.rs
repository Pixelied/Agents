use crate::{DisplayError, DisplayId, DisplaySurface};
use glam::Vec2;
use std::collections::HashSet;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Edge {
    Left,
    Right,
    Top,
    Bottom,
}
#[derive(Clone, Copy, Debug)]
struct Connection {
    from: DisplayId,
    to: DisplayId,
    edge: Edge,
    from_start: f32,
    to_start: f32,
    length: f32,
}
#[derive(Clone, Copy, Debug)]
pub struct SurfaceCrossing {
    pub display: DisplayId,
    pub position_mm: Vec2,
    pub velocity_mm_s: Vec2,
}
#[derive(Clone, Debug)]
pub struct DisplayTopology {
    displays: Vec<DisplaySurface>,
    connections: Vec<Connection>,
    continuous: bool,
}
impl DisplayTopology {
    pub fn new(mut displays: Vec<DisplaySurface>, continuous: bool) -> Result<Self, DisplayError> {
        let mut ids = HashSet::new();
        for display in &displays {
            display.validate()?;
            if !ids.insert(display.id) {
                return Err(DisplayError("duplicate display id"));
            }
        }
        displays.sort_by_key(|d| d.id);
        let mut connections = Vec::new();
        if continuous {
            for a in &displays {
                for b in &displays {
                    if a.id == b.id || !a.calibration.is_ready() || !b.calibration.is_ready() {
                        continue;
                    }
                    let ar = a.desktop_bounds;
                    let br = b.desktop_bounds;
                    let edge = if (ar.right() - br.x).abs() < 0.01 {
                        Some(Edge::Right)
                    } else if (ar.x - br.right()).abs() < 0.01 {
                        Some(Edge::Left)
                    } else if (ar.bottom() - br.y).abs() < 0.01 {
                        Some(Edge::Bottom)
                    } else if (ar.y - br.bottom()).abs() < 0.01 {
                        Some(Edge::Top)
                    } else {
                        None
                    };
                    let Some(edge) = edge else { continue };
                    let vertical = matches!(edge, Edge::Left | Edge::Right);
                    let (a0, alen, b0, blen, amm, bmm) = if vertical {
                        (
                            ar.y,
                            ar.height,
                            br.y,
                            br.height,
                            a.size_mm().y,
                            b.size_mm().y,
                        )
                    } else {
                        (ar.x, ar.width, br.x, br.width, a.size_mm().x, b.size_mm().x)
                    };
                    let lo = a0.max(b0);
                    let hi = (a0 + alen).min(b0 + blen);
                    if hi <= lo {
                        continue;
                    }
                    connections.push(Connection {
                        from: a.id,
                        to: b.id,
                        edge,
                        from_start: ((lo - a0) / alen) as f32 * amm,
                        to_start: ((lo - b0) / blen) as f32 * bmm,
                        length: (((hi - lo) / alen) as f32 * amm)
                            .min(((hi - lo) / blen) as f32 * bmm),
                    });
                }
            }
        }
        Ok(Self {
            displays,
            connections,
            continuous,
        })
    }
    pub fn displays(&self) -> &[DisplaySurface] {
        &self.displays
    }
    pub fn display(&self, id: DisplayId) -> Option<&DisplaySurface> {
        self.displays.iter().find(|d| d.id == id)
    }
    pub fn connection_count(&self) -> usize {
        self.connections.len()
    }
    pub fn continuous(&self) -> bool {
        self.continuous
    }
    pub fn connected_at(&self, id: DisplayId, edge: Edge, along_mm: f32) -> bool {
        self.connections.iter().any(|c| {
            c.from == id
                && c.edge == edge
                && along_mm >= c.from_start
                && along_mm <= c.from_start + c.length
        })
    }
    pub fn crossing(&self, id: DisplayId, p: Vec2, v: Vec2) -> Option<SurfaceCrossing> {
        let size = self.display(id)?.size_mm();
        for c in self.connections.iter().filter(|c| c.from == id) {
            let (crosses, along, overshoot) = match c.edge {
                Edge::Right => (p.x >= size.x && v.x > 0.0, p.y, p.x - size.x),
                Edge::Left => (p.x < 0.0 && v.x < 0.0, p.y, -p.x),
                Edge::Bottom => (p.y >= size.y && v.y > 0.0, p.x, p.y - size.y),
                Edge::Top => (p.y < 0.0 && v.y < 0.0, p.x, -p.y),
            };
            if !crosses || along < c.from_start || along > c.from_start + c.length {
                continue;
            }
            let target = self.display(c.to)?.size_mm();
            let along = c.to_start + along - c.from_start;
            let position_mm = match c.edge {
                Edge::Right => Vec2::new(overshoot, along),
                Edge::Left => Vec2::new(target.x - overshoot, along),
                Edge::Bottom => Vec2::new(along, overshoot),
                Edge::Top => Vec2::new(along, target.y - overshoot),
            };
            return Some(SurfaceCrossing {
                display: c.to,
                position_mm,
                velocity_mm_s: v,
            });
        }
        None
    }
}
