use crate::lifecycle::Lifecycle;
use display_model::DisplayId;
use platform_api::ForegroundContext;
use settings::{AppConfig, AppExclusionMode};
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum VisibilityDecision {
    Show,
    HideCompatibility,
    HidePanic,
    HidePaused,
    HideUnsafe,
    HideDisabled,
    HideSuspended,
}
/// Policy consumes only stable process identity, fullscreen metadata and display identity.
pub fn visibility(
    config: &AppConfig,
    life: &Lifecycle,
    display: DisplayId,
    enabled: bool,
    safe: bool,
    foreground: Option<&ForegroundContext>,
) -> VisibilityDecision {
    if !safe {
        return VisibilityDecision::HideUnsafe;
    }
    if life.suspended {
        return VisibilityDecision::HideSuspended;
    }
    if life.panic_hidden || life.quit {
        return VisibilityDecision::HidePanic;
    }
    if config.paused {
        return VisibilityDecision::HidePaused;
    }
    if !config.enabled || !enabled {
        return VisibilityDecision::HideDisabled;
    }
    if let Some(fg) = foreground {
        if config.safe_overlay_mode && fg.display.is_none() {
            return VisibilityDecision::HideCompatibility;
        }
        let here = fg.display.is_none_or(|id| id == display);
        if here {
            let excluded = config.app_exclusions.iter().any(|rule| {
                rule.stable_id == fg.app.stable_id
                    && (rule.mode == AppExclusionMode::Hide || fg.fullscreen)
            });
            if excluded || (config.safe_overlay_mode && fg.fullscreen) {
                return VisibilityDecision::HideCompatibility;
            }
        }
    } else if config.safe_overlay_mode {
        // Unknown foreground metadata is not positive evidence that safe mode permits presentation.
        return VisibilityDecision::HideCompatibility;
    }
    VisibilityDecision::Show
}
