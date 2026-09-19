use display_model::{DisplayId, DisplaySurface, Vec2Mm};
use platform_api::DesktopCursor;
use simulation::CursorDisturbance;
use std::time::Duration;
#[derive(Default)]
pub struct CursorTracker {
    previous: Option<(Duration, DisplayId, glam::Vec2)>,
}
impl CursorTracker {
    pub fn sample(
        &mut self,
        now: Duration,
        cursor: Option<DesktopCursor>,
        displays: &[DisplaySurface],
    ) -> Option<CursorDisturbance> {
        let Some(cursor) = cursor.filter(|c| c.x.is_finite() && c.y.is_finite()) else {
            self.previous = None;
            return None;
        };
        let Some(display) = displays
            .iter()
            .find(|d| d.calibration.is_ready() && d.desktop_bounds.contains(cursor.x, cursor.y))
        else {
            self.previous = None;
            return None;
        };
        let position = display.desktop_to_mm(cursor.x, cursor.y);
        let old = self.previous.replace((now, display.id, position));
        let (before, previous_display, previous_position) = old?;
        let dt = now.checked_sub(before)?;
        // UI disturbance engineering policy: no teleport across monitors or after inactivity.
        if previous_display != display.id || dt.is_zero() || dt > Duration::from_millis(250) {
            return None;
        }
        let velocity = (position - previous_position) / dt.as_secs_f32();
        if !velocity.is_finite() {
            return None;
        }
        Some(CursorDisturbance {
            display: display.id,
            position_mm: Vec2Mm::new(position.x, position.y).ok()?,
            velocity_mm_s: velocity,
            strength: (velocity.length() / 100.0).clamp(0.0, 1.0),
        })
    }
}
