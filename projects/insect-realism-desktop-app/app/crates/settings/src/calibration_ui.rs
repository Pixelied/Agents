use display_model::{DisplayCalibration, DisplayError, DisplayId};

/// egui uses logical points; calibration is always measured in physical pixels.
pub fn reference_points(physical_pixels: f32, pixels_per_point: f32) -> Result<f32, DisplayError> {
    if !physical_pixels.is_finite()
        || physical_pixels <= 0.0
        || !pixels_per_point.is_finite()
        || pixels_per_point <= 0.0
    {
        return Err(DisplayError("reference pixels or UI scale"));
    }
    Ok(physical_pixels / pixels_per_point)
}

pub fn accept_reference(
    display: DisplayId,
    window_display: Option<DisplayId>,
    pixels: f32,
    fully_visible: bool,
) -> Result<DisplayCalibration, DisplayError> {
    if window_display != Some(display) {
        return Err(DisplayError(
            "calibration window is on another or unknown monitor",
        ));
    }
    if !fully_visible {
        return Err(DisplayError("the entire reference must be visible"));
    }
    DisplayCalibration::from_card_width(pixels)
}
