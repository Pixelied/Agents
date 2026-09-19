use desktop_app::{compatibility::*, lifecycle::*, recovery::*};
use display_model::DisplayId;
use platform_api::{AppIdentity, ForegroundContext};
use settings::{AppConfig, AppExclusion, AppExclusionMode};
use std::time::Duration;
#[test]
fn panic_hide_freezes_without_persisting_pause() {
    let c = AppConfig::default();
    let original = c.clone();
    let mut life = Lifecycle::default();
    life.hide_all();
    assert!(life.frozen(&c));
    assert_eq!(c, original);
    life.show_all();
    assert!(!life.frozen(&c));
}
#[test]
fn closing_settings_does_not_stop_creatures() {
    let c = AppConfig::default();
    let mut life = Lifecycle::default();
    life.open_settings();
    life.close_settings();
    assert!(!life.settings_open);
    assert!(!life.frozen(&c));
    let c = AppConfig { paused: true, ..c };
    assert!(life.frozen(&c));
}
#[test]
fn visibility_is_fail_closed_before_any_native_show() {
    let c = AppConfig::default();
    let life = Lifecycle::default();
    assert_eq!(
        visibility(&c, &life, DisplayId(1), true, false, None),
        VisibilityDecision::HideUnsafe
    );
    assert_eq!(
        visibility(&c, &life, DisplayId(1), false, true, None),
        VisibilityDecision::HideDisabled
    );
}
#[test]
fn fullscreen_exclusion_affects_its_display_without_hiding_other_monitors() {
    let mut c = AppConfig {
        safe_overlay_mode: true,
        ..AppConfig::default()
    };
    let context = ForegroundContext {
        app: AppIdentity {
            stable_id: "game.exe".into(),
            display_name: "Game".into(),
        },
        fullscreen: true,
        display: Some(DisplayId(1)),
    };
    let life = Lifecycle::default();
    assert_eq!(
        visibility(&c, &life, DisplayId(1), true, true, Some(&context)),
        VisibilityDecision::HideCompatibility
    );
    assert_eq!(
        visibility(&c, &life, DisplayId(2), true, true, Some(&context)),
        VisibilityDecision::Show
    );
    c.safe_overlay_mode = false;
    c.app_exclusions.push(AppExclusion {
        stable_id: "game.exe".into(),
        mode: AppExclusionMode::Hide,
    });
    let context = ForegroundContext {
        fullscreen: false,
        ..context
    };
    assert_eq!(
        visibility(&c, &life, DisplayId(1), true, true, Some(&context)),
        VisibilityDecision::HideCompatibility
    );
}
#[test]
fn recovery_is_bounded_and_remains_hidden_until_success() {
    let mut r = Recovery::default();
    assert!(!r.ready());
    assert!(r.begin(Duration::ZERO));
    r.fail(Duration::ZERO);
    assert!(!r.begin(Duration::ZERO));
    assert!(r.begin(Duration::from_secs(1)));
    r.fail(Duration::from_secs(1));
    assert!(r.begin(Duration::from_secs(5)));
    r.fail(Duration::from_secs(5));
    assert!(r.exhausted());
    assert!(!r.begin(Duration::from_secs(500)));
    assert!(!r.ready());
}
#[test]
fn suspend_and_panic_take_precedence_over_compatibility() {
    let c = AppConfig::default();
    let mut life = Lifecycle {
        suspended: true,
        ..Lifecycle::default()
    };
    assert_eq!(
        visibility(&c, &life, DisplayId(1), true, true, None),
        VisibilityDecision::HideSuspended
    );
    life.hide_all();
    assert!(life.frozen(&c));
}
#[test]
fn stalled_driver_timeout_stops_automatic_retries() {
    let mut r = desktop_app::recovery::Recovery::default();
    assert!(r.begin(std::time::Duration::ZERO));
    r.stop_permanently();
    assert!(r.exhausted());
    assert!(!r.begin(std::time::Duration::from_secs(100)));
    assert!(r.deadline().is_none());
}
#[test]
fn safe_mode_hides_when_application_identity_is_known_but_display_geometry_is_not() {
    let c = AppConfig {
        safe_overlay_mode: true,
        ..AppConfig::default()
    };
    let fg = ForegroundContext {
        app: AppIdentity {
            stable_id: "restricted-metadata".into(),
            display_name: "App".into(),
        },
        fullscreen: false,
        display: None,
    };
    assert_eq!(
        visibility(
            &c,
            &Lifecycle::default(),
            DisplayId(1),
            true,
            true,
            Some(&fg)
        ),
        VisibilityDecision::HideCompatibility
    );
}
