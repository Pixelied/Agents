use display_model::DisplayId;
#[derive(Clone, Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct AppIdentity {
    pub stable_id: String,
    pub display_name: String,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ForegroundContext {
    pub app: AppIdentity,
    pub fullscreen: bool,
    pub display: Option<DisplayId>,
}
#[derive(Clone, Copy, Debug)]
pub struct DesktopCursor {
    pub x: f64,
    pub y: f64,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum UtilityAction {
    ToggleVisible,
    TogglePause,
    OpenSettings,
    SetPreset(String),
    ToggleLaunchAtLogin,
    Quit,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum PlatformEvent {
    DisplaysChanged,
    Suspend,
    Resume,
    ForegroundChanged(Option<ForegroundContext>),
    PanicHotkey,
    UtilityAction(UtilityAction),
    QuitRequested,
}
#[derive(Clone, Debug, Default)]
pub struct UtilityState {
    pub visible: bool,
    pub paused: bool,
    pub launch_at_login: bool,
    pub preset: String,
    pub diagnostic: Option<String>,
}

/// Bounded native-event inbox. Implemented independently of any OS event loop.
#[derive(Default)]
pub struct EventQueue {
    pending: std::collections::VecDeque<PlatformEvent>,
}
impl EventQueue {
    pub fn push(&mut self, event: PlatformEvent) {
        use PlatformEvent::*;
        let lifecycle = matches!(event, Suspend | Resume);
        if lifecycle {
            self.pending.retain(|e| !matches!(e, Suspend | Resume));
        }
        if matches!(event, DisplaysChanged | PanicHotkey | QuitRequested)
            && self.pending.contains(&event)
        {
            return;
        }
        if self.pending.len() >= 256 {
            let expendable = self
                .pending
                .iter()
                .position(|e| !matches!(e, PanicHotkey | Suspend | Resume | QuitRequested));
            if let Some(index) = expendable {
                self.pending.remove(index);
            } else {
                return;
            }
        }
        self.pending.push_back(event);
    }
    pub fn drain(&mut self) -> Vec<PlatformEvent> {
        self.pending.drain(..).collect()
    }
}
