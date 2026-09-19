pub const STANDARD_THRESHOLD: f32 = TINY_BOUNDARY_PX;
pub const DETAIL_THRESHOLD: f32 = DETAILED_BOUNDARY_PX;
/// LOD thresholds are physical pixel coverage, never OS logical UI scale.
pub const TINY_BOUNDARY_PX: f32 = 12.;
pub const DETAILED_BOUNDARY_PX: f32 = 32.;
pub const LOD_HYSTERESIS_PX: f32 = 1.5;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u32)]
pub enum Lod {
    Tiny = 0,
    Standard = 1,
    Detailed = 2,
}
pub fn select_lod(px: f32, previous: Option<Lod>) -> Lod {
    if !px.is_finite() {
        return Lod::Tiny;
    }
    match previous {
        Some(Lod::Tiny) if px < TINY_BOUNDARY_PX + LOD_HYSTERESIS_PX => Lod::Tiny,
        Some(Lod::Standard)
            if px > TINY_BOUNDARY_PX - LOD_HYSTERESIS_PX
                && px < DETAILED_BOUNDARY_PX + LOD_HYSTERESIS_PX =>
        {
            Lod::Standard
        }
        Some(Lod::Detailed) if px > DETAILED_BOUNDARY_PX - LOD_HYSTERESIS_PX => Lod::Detailed,
        _ if px < TINY_BOUNDARY_PX => Lod::Tiny,
        _ if px < DETAILED_BOUNDARY_PX => Lod::Standard,
        _ => Lod::Detailed,
    }
}
