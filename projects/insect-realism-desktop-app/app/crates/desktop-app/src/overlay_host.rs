use display_model::DisplayId;
use platform_api::*;
/// Drop order is deliberate: GPU resources first, then native window. The real GPU
/// surface additionally retains its own native owner through SurfaceSource.
pub struct OverlayHost<S> {
    surface: Option<S>,
    window: Box<dyn OverlayWindow>,
}
impl<S> OverlayHost<S> {
    pub fn new<E: std::fmt::Display>(
        window: Box<dyn OverlayWindow>,
        create: impl FnOnce(&dyn OverlayWindow) -> Result<S, E>,
    ) -> Result<Self, PlatformError> {
        window.mark_unsafe();
        if let Err(e) = window.verify_input_passthrough() {
            window.mark_unsafe();
            return Err(e);
        }
        let surface = match create(window.as_ref()) {
            Ok(s) => s,
            Err(e) => {
                window.mark_unsafe();
                return Err(PlatformError::Native(e.to_string()));
            }
        };
        if let Err(e) = window.verify_input_passthrough() {
            window.mark_unsafe();
            drop(surface);
            return Err(e);
        }
        Ok(Self {
            surface: Some(surface),
            window,
        })
    }
    pub fn set_visible(&self, v: bool) -> Result<(), PlatformError> {
        if v && !self.safe() {
            return Err(PlatformError::Unsafe("GPU surface/native safety not ready"));
        }
        if self.window.is_visible() == v {
            return Ok(());
        }
        self.window.set_visible(v)
    }
    pub fn invalidate(&mut self) {
        self.window.mark_unsafe();
        self.surface = None;
    }
    pub fn safe(&self) -> bool {
        self.surface.is_some() && self.window.safety_state() == OverlaySafetyState::SafeToShow
    }
    pub fn is_visible(&self) -> bool {
        self.window.is_visible()
    }
    pub fn display_id(&self) -> DisplayId {
        self.window.display_id()
    }
    pub fn present_if_visible<R, E>(
        &mut self,
        present: impl FnOnce(&mut S) -> Result<R, E>,
    ) -> Result<Option<R>, E> {
        if !self.safe() || !self.window.is_visible() {
            return Ok(None);
        }
        let Some(surface) = &mut self.surface else {
            return Ok(None);
        };
        match present(surface) {
            Ok(value) => Ok(Some(value)),
            Err(error) => {
                self.invalidate();
                Err(error)
            }
        }
    }
    pub fn surface_mut(&mut self) -> Option<&mut S> {
        self.surface.as_mut()
    }
}
impl<S> Drop for OverlayHost<S> {
    fn drop(&mut self) {
        let _ = self.window.set_visible(false);
    }
}
