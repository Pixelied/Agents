use crate::{DisplayId, Vec2Mm};
use glam::Vec2;
use serde::{Deserialize, Serialize};
use thiserror::Error;
#[derive(Debug, Error)]
#[error("invalid display geometry or calibration: {0}")]
pub struct DisplayError(pub &'static str);
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct PixelSize {
    pub width: u32,
    pub height: u32,
}
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct DesktopRect {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}
impl DesktopRect {
    pub fn right(self) -> f64 {
        self.x + self.width
    }
    pub fn bottom(self) -> f64 {
        self.y + self.height
    }
    pub fn contains(self, x: f64, y: f64) -> bool {
        x >= self.x && x < self.right() && y >= self.y && y < self.bottom()
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum CalibrationConfidence {
    TrustedMetadata,
    Manual,
    NeedsManual,
}
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct DisplayCalibration {
    pub mm_per_physical_px: f32,
    pub confidence: CalibrationConfidence,
}
impl DisplayCalibration {
    pub fn validate(self) -> Result<(), DisplayError> {
        if !self.mm_per_physical_px.is_finite() || !(0.02..=1.0).contains(&self.mm_per_physical_px)
        {
            return Err(DisplayError("millimeters per physical pixel"));
        }
        Ok(())
    }
    pub fn is_ready(self) -> bool {
        self.confidence != CalibrationConfidence::NeedsManual
    }
    pub fn from_card_width(physical_pixels: f32) -> Result<Self, DisplayError> {
        let c = Self {
            mm_per_physical_px: 85.60 / physical_pixels,
            confidence: CalibrationConfidence::Manual,
        };
        c.validate()?;
        Ok(c)
    }
    /// An explicit saved calibration overrides metadata. The provisional 96-PPI
    /// ruler is only a UI starting point; NeedsManual displays must not show ants.
    pub fn resolve(
        manual: Option<f32>,
        trusted_metadata: Option<f32>,
    ) -> Result<Self, DisplayError> {
        if let Some(mm) = manual {
            let c = Self {
                mm_per_physical_px: mm,
                confidence: CalibrationConfidence::Manual,
            };
            c.validate()?;
            return Ok(c);
        }
        if let Some(mm) = trusted_metadata {
            let c = Self {
                mm_per_physical_px: mm,
                confidence: CalibrationConfidence::TrustedMetadata,
            };
            if c.validate().is_ok() {
                return Ok(c);
            }
        }
        Ok(Self {
            mm_per_physical_px: 25.4 / 96.0,
            confidence: CalibrationConfidence::NeedsManual,
        })
    }
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct DisplaySurface {
    pub id: DisplayId,
    pub fingerprint: String,
    pub name: String,
    pub pixels: PixelSize,
    /// OS desktop coordinate units, not necessarily physical pixels on macOS.
    pub desktop_bounds: DesktopRect,
    pub scale_factor: f64,
    pub refresh_hz: f32,
    pub rotation_deg: u16,
    pub calibration: DisplayCalibration,
}
impl DisplaySurface {
    pub fn validate(&self) -> Result<(), DisplayError> {
        let r = self.desktop_bounds;
        if ![r.x, r.y, r.width, r.height, self.scale_factor]
            .iter()
            .all(|n| n.is_finite())
            || r.width <= 0.0
            || r.height <= 0.0
            || r.width > 100_000.0
            || r.height > 100_000.0
            || self.scale_factor <= 0.0
            || self.scale_factor > 16.0
        {
            return Err(DisplayError("desktop bounds or UI scale"));
        }
        if self.pixels.width == 0
            || self.pixels.height == 0
            || self.pixels.width > 65_536
            || self.pixels.height > 65_536
        {
            return Err(DisplayError("pixel dimensions"));
        }
        if !self.refresh_hz.is_finite() || !(1.0..=1000.0).contains(&self.refresh_hz) {
            return Err(DisplayError("refresh rate"));
        }
        if ![0, 90, 180, 270].contains(&self.rotation_deg) {
            return Err(DisplayError("rotation"));
        }
        if self.fingerprint.is_empty() {
            return Err(DisplayError("fingerprint"));
        }
        self.calibration.validate()
    }
    pub fn size_mm(&self) -> Vec2 {
        Vec2::new(self.pixels.width as f32, self.pixels.height as f32)
            * self.calibration.mm_per_physical_px
    }
    pub fn mm_to_physical_px(&self, point: Vec2Mm) -> Vec2 {
        point.as_vec2() / self.calibration.mm_per_physical_px
    }
    pub fn desktop_to_mm(&self, x: f64, y: f64) -> Vec2 {
        let r = self.desktop_bounds;
        let size = self.size_mm();
        Vec2::new(
            ((x - r.x) / r.width) as f32 * size.x,
            ((y - r.y) / r.height) as f32 * size.y,
        )
    }
}
