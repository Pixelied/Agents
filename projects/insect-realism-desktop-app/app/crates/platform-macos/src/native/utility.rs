use super::*;
use objc2::{DefinedClass, MainThreadOnly, define_class, msg_send, sel};
use objc2_app_kit::*;
use objc2_foundation::{NSNotification, NSNotificationCenter, NSObject, NSObjectProtocol};

define_class!(
    #[unsafe(super=NSObject)] #[thread_kind=MainThreadOnly] #[ivars=Events]
    struct Target;
    unsafe impl NSObjectProtocol for Target {}
    impl Target {
        #[unsafe(method(perform:))]
        fn perform(&self,sender:&NSMenuItem){
            let action=match sender.tag(){1=>UtilityAction::ToggleVisible,2=>UtilityAction::TogglePause,3=>UtilityAction::OpenSettings,4=>UtilityAction::ToggleLaunchAtLogin,5=>UtilityAction::Quit,10=>UtilityAction::SetPreset("realistic".into()),11=>UtilityAction::SetPreset("light".into()),12=>UtilityAction::SetPreset("heavy".into()),13=>UtilityAction::SetPreset("nightmare".into()),_=>return};
            emit(self.ivars(),PlatformEvent::UtilityAction(action));
        }
        #[unsafe(method(displays:))] fn displays(&self,_n:&NSNotification){emit(self.ivars(),PlatformEvent::DisplaysChanged);}
        #[unsafe(method(suspend:))] fn suspend(&self,_n:&NSNotification){emit(self.ivars(),PlatformEvent::Suspend);}
        #[unsafe(method(resume:))] fn resume(&self,_n:&NSNotification){emit(self.ivars(),PlatformEvent::Resume);emit(self.ivars(),PlatformEvent::DisplaysChanged);}
    }
);
pub(super) struct Utility {
    item: Retained<NSStatusItem>,
    target: Retained<Target>,
    menu: Retained<NSMenu>,
    visible: Retained<NSMenuItem>,
    paused: Retained<NSMenuItem>,
    login: Retained<NSMenuItem>,
}
impl Utility {
    pub fn new(mtm: MainThreadMarker, events: Events) -> Result<Self, PlatformError> {
        let target = Target::alloc(mtm).set_ivars(events);
        let target: Retained<Target> = unsafe { msg_send![super(target), init] };
        let menu = NSMenu::new(mtm);
        menu.setAutoenablesItems(false);
        let mut items = Vec::new();
        for (tag, title) in [
            (1, "Hide All"),
            (2, "Pause"),
            (3, "Settings..."),
            (10, "Realistic"),
            (11, "Light Infestation"),
            (12, "Heavy Infestation"),
            (13, "Nightmare"),
            (4, "Launch at Login"),
            (5, "Quit"),
        ] {
            // SAFETY: Target is retained by Utility for at least as long as menu items; selector signature matches Target::perform.
            let item = unsafe {
                NSMenuItem::initWithTitle_action_keyEquivalent(
                    NSMenuItem::alloc(mtm),
                    &NSString::from_str(title),
                    Some(sel!(perform:)),
                    &NSString::new(),
                )
            };
            item.setTag(tag);
            unsafe { item.setTarget(Some(&target)) };
            item.setEnabled(true);
            menu.addItem(&item);
            items.push(item);
        }
        let item = NSStatusBar::systemStatusBar().statusItemWithLength(-1.);
        if let Some(button) = item.button(mtm) {
            button.setTitle(&NSString::from_str("Ants"));
        }
        item.setMenu(Some(&menu));
        item.setVisible(true);
        let center = NSNotificationCenter::defaultCenter();
        let workspace = NSWorkspace::sharedWorkspace().notificationCenter();
        // SAFETY: Selectors have NSNotification arguments, observer is retained, and removed before target deallocation.
        unsafe {
            center.addObserver_selector_name_object(
                &target,
                sel!(displays:),
                Some(NSApplicationDidChangeScreenParametersNotification),
                None,
            );
            workspace.addObserver_selector_name_object(
                &target,
                sel!(suspend:),
                Some(NSWorkspaceWillSleepNotification),
                None,
            );
            workspace.addObserver_selector_name_object(
                &target,
                sel!(resume:),
                Some(NSWorkspaceDidWakeNotification),
                None,
            );
            workspace.addObserver_selector_name_object(
                &target,
                sel!(suspend:),
                Some(NSWorkspaceSessionDidResignActiveNotification),
                None,
            );
            workspace.addObserver_selector_name_object(
                &target,
                sel!(resume:),
                Some(NSWorkspaceSessionDidBecomeActiveNotification),
                None,
            );
        }
        Ok(Self {
            item,
            target,
            menu,
            visible: items[0].clone(),
            paused: items[1].clone(),
            login: items[7].clone(),
        })
    }
    pub fn update(&mut self, state: &UtilityState) {
        self.visible.setTitle(&NSString::from_str(if state.visible {
            "Hide All"
        } else {
            "Show Creatures"
        }));
        self.paused.setTitle(&NSString::from_str(if state.paused {
            "Resume"
        } else {
            "Pause"
        }));
        self.login.setState(if state.launch_at_login {
            NSControlStateValueOn
        } else {
            NSControlStateValueOff
        });
        if let Some(button) = self.item.button(self.target.mtm()) {
            button.setToolTip(Some(&NSString::from_str(
                state.diagnostic.as_deref().unwrap_or("Insect Realism"),
            )));
        }
    }
}
impl Drop for Utility {
    fn drop(&mut self) {
        unsafe {
            NSNotificationCenter::defaultCenter().removeObserver(&self.target);
            NSWorkspace::sharedWorkspace()
                .notificationCenter()
                .removeObserver(&self.target);
        }
        for item in self.menu.itemArray().iter() {
            unsafe { item.setTarget(None) };
        }
        NSStatusBar::systemStatusBar().removeStatusItem(&self.item);
    }
}
