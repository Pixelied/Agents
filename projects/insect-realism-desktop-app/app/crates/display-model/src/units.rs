use serde::{Deserialize, Serialize};
use thiserror::Error;
#[derive(Debug, Error)]
#[error("physical units must be finite and lengths nonnegative")]
pub struct UnitError;
#[derive(Clone, Copy, Debug, PartialEq, PartialOrd)]
pub struct Millimeters(f32);
impl Millimeters {
    pub fn new(value: f32) -> Result<Self, UnitError> {
        if value.is_finite() && value >= 0.0 {
            Ok(Self(value))
        } else {
            Err(UnitError)
        }
    }
    pub const fn get(self) -> f32 {
        self.0
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Default, Serialize, Deserialize)]
pub struct Vec2Mm {
    pub x: f32,
    pub y: f32,
}
impl Vec2Mm {
    pub fn new(x: f32, y: f32) -> Result<Self, UnitError> {
        if x.is_finite() && y.is_finite() {
            Ok(Self { x, y })
        } else {
            Err(UnitError)
        }
    }
    pub fn as_vec2(self) -> glam::Vec2 {
        glam::Vec2::new(self.x, self.y)
    }
}
impl From<glam::Vec2> for Vec2Mm {
    fn from(v: glam::Vec2) -> Self {
        Self { x: v.x, y: v.y }
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
pub struct DisplayId(pub u64);
