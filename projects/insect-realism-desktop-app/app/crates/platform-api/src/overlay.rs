use crate::PlatformError;
use raw_window_handle::{RawDisplayHandle, RawWindowHandle};
use std::{any::Any, cell::Cell, rc::Rc};
#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub enum OverlaySafetyState {
    Created,
    Transparent,
    NonActivating,
    ClickThrough,
    SafeToShow,
    HiddenUnsafe,
}
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeSafetyProof {
    pub transparent: bool,
    pub non_activating: bool,
    pub click_through: bool,
    pub not_task_switcher: bool,
}
pub struct SafetyGate {
    state: Cell<OverlaySafetyState>,
    visible: Cell<bool>,
}
impl Default for SafetyGate {
    fn default() -> Self {
        Self {
            state: Cell::new(OverlaySafetyState::Created),
            visible: Cell::new(false),
        }
    }
}
impl SafetyGate {
    pub fn state(&self) -> OverlaySafetyState {
        self.state.get()
    }
    pub fn is_visible(&self) -> bool {
        self.visible.get()
    }
    pub fn verify(&self, proof: NativeSafetyProof) -> Result<(), PlatformError> {
        if !(proof.transparent
            && proof.non_activating
            && proof.click_through
            && proof.not_task_switcher)
        {
            self.invalidate();
            return Err(PlatformError::Unsafe(
                "native transparency/focus/pass-through/task-switcher check failed",
            ));
        }
        self.state.set(OverlaySafetyState::SafeToShow);
        Ok(())
    }
    pub fn authorize_show(&self) -> Result<(), PlatformError> {
        if self.state.get() != OverlaySafetyState::SafeToShow {
            return Err(PlatformError::Unsafe(
                "input safety has not been established",
            ));
        }
        self.visible.set(true);
        Ok(())
    }
    pub fn hide(&self) {
        self.visible.set(false);
    }
    pub fn invalidate(&self) {
        self.hide();
        self.state.set(OverlaySafetyState::HiddenUnsafe);
    }
}
/// Keeps the real native window/view owner alive until the GPU surface is dropped.
/// Rc intentionally makes this main-thread-only: raw AppKit/Win32 handles must not migrate.
#[derive(Clone)]
pub struct SurfaceSource {
    display: RawDisplayHandle,
    window: RawWindowHandle,
    _owner: Rc<dyn Any>,
}
impl SurfaceSource {
    /// # Safety
    /// Both handles must be valid and refer to the same live native window/display.
    /// `owner` must retain the native window AND any view used in `window`; they must
    /// not be destroyed/replaced while any clone of this source exists. Construction,
    /// use and destruction must occur on their owning native event-loop thread.
    pub unsafe fn from_raw(
        display: RawDisplayHandle,
        window: RawWindowHandle,
        owner: Rc<dyn Any>,
    ) -> Self {
        Self {
            display,
            window,
            _owner: owner,
        }
    }
    pub fn raw_display(&self) -> RawDisplayHandle {
        self.display
    }
    pub fn raw_window(&self) -> RawWindowHandle {
        self.window
    }
}
impl std::fmt::Debug for SurfaceSource {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("SurfaceSource")
            .field("display", &self.display)
            .field("window", &self.window)
            .finish_non_exhaustive()
    }
}
