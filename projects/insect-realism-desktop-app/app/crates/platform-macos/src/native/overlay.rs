use super::*;
use objc2::{MainThreadOnly, define_class, msg_send};
use objc2_app_kit::{
    NSBackingStoreType, NSColor, NSPanel, NSStatusWindowLevel, NSWindowCollectionBehavior,
    NSWindowStyleMask,
};
use objc2_foundation::{NSObjectProtocol, NSPoint, NSRect, NSSize};
use raw_window_handle::{AppKitDisplayHandle, AppKitWindowHandle, RawDisplayHandle};
use std::ptr::NonNull;

define_class!(
    #[unsafe(super=NSPanel)]
    #[thread_kind=MainThreadOnly]
    struct AntPanel;
    unsafe impl NSObjectProtocol for AntPanel {}
    impl AntPanel {
        #[unsafe(method(canBecomeKeyWindow))] fn can_become_key(&self)->bool {false}
        #[unsafe(method(canBecomeMainWindow))] fn can_become_main(&self)->bool {false}
    }
);
struct NativeWindow {
    panel: Retained<AntPanel>,
    view: Retained<NSView>,
}
impl Drop for NativeWindow {
    fn drop(&mut self) {
        self.panel.orderOut(None);
        self.panel.close();
    }
}
pub(super) struct Overlay {
    id: DisplayId,
    native: Rc<NativeWindow>,
    gate: SafetyGate,
}
impl Overlay {
    pub(super) fn new(mtm: MainThreadMarker, d: &DisplaySurface) -> Result<Self, PlatformError> {
        let screen = display::screen_for(mtm, d.id)
            .ok_or(PlatformError::Native("display disappeared".into()))?;
        let frame = screen.frame();
        let style = NSWindowStyleMask::Borderless | NSWindowStyleMask::NonactivatingPanel;
        // SAFETY: Correct NSPanel initializer ABI; retained ownership, release-on-close explicitly disabled.
        let panel: Retained<AntPanel> = unsafe {
            msg_send![AntPanel::alloc(mtm),initWithContentRect:frame,styleMask:style,backing:NSBackingStoreType::Buffered,defer:false]
        };
        unsafe { panel.setReleasedWhenClosed(false) };
        panel.setOpaque(false);
        panel.setBackgroundColor(Some(&NSColor::clearColor()));
        panel.setHasShadow(false);
        panel.setIgnoresMouseEvents(true);
        panel.setHidesOnDeactivate(false);
        panel.setMovable(false);
        panel.setFloatingPanel(true);
        panel.setWorksWhenModal(true);
        panel.setBecomesKeyOnlyIfNeeded(true);
        panel.setCollectionBehavior(
            NSWindowCollectionBehavior::CanJoinAllSpaces
                | NSWindowCollectionBehavior::FullScreenAuxiliary
                | NSWindowCollectionBehavior::Stationary
                | NSWindowCollectionBehavior::IgnoresCycle,
        );
        panel.setLevel(NSStatusWindowLevel);
        let view = NSView::initWithFrame(
            NSView::alloc(mtm),
            NSRect::new(
                NSPoint::new(0., 0.),
                NSSize::new(frame.size.width, frame.size.height),
            ),
        );
        panel.setContentView(Some(&view));
        panel.orderOut(None);
        let this = Self {
            id: d.id,
            native: Rc::new(NativeWindow { panel, view }),
            gate: SafetyGate::default(),
        };
        this.verify_input_passthrough()?;
        Ok(this)
    }
}
impl OverlayWindow for Overlay {
    fn display_id(&self) -> DisplayId {
        self.id
    }
    fn safety_state(&self) -> OverlaySafetyState {
        self.gate.state()
    }
    fn verify_input_passthrough(&self) -> Result<(), PlatformError> {
        let p = &self.native.panel;
        let result = self.gate.verify(NativeSafetyProof {
            transparent: !p.isOpaque(),
            non_activating: !p.canBecomeKeyWindow()
                && !p.canBecomeMainWindow()
                && p.styleMask()
                    .contains(NSWindowStyleMask::NonactivatingPanel),
            click_through: p.ignoresMouseEvents(),
            not_task_switcher: p
                .collectionBehavior()
                .contains(NSWindowCollectionBehavior::IgnoresCycle),
        });
        if result.is_err() {
            p.orderOut(None);
        }
        result
    }
    fn set_visible(&self, visible: bool) -> Result<(), PlatformError> {
        if visible {
            self.verify_input_passthrough()?;
            self.gate.authorize_show()?;
            self.native.panel.orderFrontRegardless();
        } else {
            self.native.panel.orderOut(None);
            self.gate.hide();
        }
        Ok(())
    }
    fn is_visible(&self) -> bool {
        self.gate.is_visible() && self.native.panel.isVisible()
    }
    fn mark_unsafe(&self) {
        self.native.panel.orderOut(None);
        self.gate.invalidate();
    }
    fn surface_source(&self) -> Result<SurfaceSource, PlatformError> {
        self.verify_input_passthrough()?;
        let handle = AppKitWindowHandle::new(NonNull::from(&*self.native.view).cast());
        // SAFETY: Rc owner retains panel AND NSView. Destruction occurs only after every GPU source drops.
        Ok(unsafe {
            SurfaceSource::from_raw(
                RawDisplayHandle::AppKit(AppKitDisplayHandle::new()),
                RawWindowHandle::AppKit(handle),
                self.native.clone(),
            )
        })
    }
}
impl Drop for Overlay {
    fn drop(&mut self) {
        self.native.panel.orderOut(None);
    }
}
