use platform_api::*;
#[test]
fn event_storms_are_bounded_and_cannot_evict_panic_hide() {
    let mut q = EventQueue::default();
    q.push(PlatformEvent::PanicHotkey);
    for _ in 0..1000 {
        q.push(PlatformEvent::UtilityAction(UtilityAction::OpenSettings));
    }
    let events = q.drain();
    assert!(events.len() <= 256);
    assert!(events.contains(&PlatformEvent::PanicHotkey));
}
#[test]
fn topology_churn_is_coalesced_but_deliberate_toggles_are_not() {
    let mut q = EventQueue::default();
    for _ in 0..1000 {
        q.push(PlatformEvent::DisplaysChanged);
    }
    q.push(PlatformEvent::UtilityAction(UtilityAction::ToggleVisible));
    q.push(PlatformEvent::UtilityAction(UtilityAction::ToggleVisible));
    assert_eq!(q.drain().len(), 3);
}
#[test]
fn latest_sleep_state_wins_without_erasing_panic() {
    let mut q = EventQueue::default();
    q.push(PlatformEvent::Suspend);
    q.push(PlatformEvent::PanicHotkey);
    q.push(PlatformEvent::Resume);
    let events = q.drain();
    assert!(!events.contains(&PlatformEvent::Suspend));
    assert!(events.contains(&PlatformEvent::Resume));
    assert!(events.contains(&PlatformEvent::PanicHotkey));
}
