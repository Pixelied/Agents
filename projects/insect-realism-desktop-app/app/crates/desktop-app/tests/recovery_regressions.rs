use desktop_app::recovery::Recovery;
use std::time::Duration;
#[test]
fn initialization_success_does_not_erase_repeated_first_frame_failures() {
    let mut r = Recovery::default();
    for seconds in [0, 2, 5] {
        let now = Duration::from_secs(seconds);
        assert!(r.begin(now));
        r.succeed();
        r.fail(now + Duration::from_millis(1));
    }
    assert!(
        r.exhausted(),
        "initialization success replenished the retry budget"
    );
    assert!(!r.begin(Duration::from_secs(100)));
}
#[test]
fn explicit_user_retry_resets_exhaustion() {
    let mut r = Recovery::default();
    for seconds in [0, 2, 5] {
        let t = Duration::from_secs(seconds);
        assert!(r.begin(t));
        r.fail(t);
    }
    assert!(r.exhausted());
    r.retry_by_user();
    assert!(r.begin(Duration::from_secs(6)));
    assert_eq!(r.attempts, 1);
}

#[test]
fn only_sustained_success_replenishes_retry_budget() {
    let mut r = Recovery::default();
    assert!(r.begin(Duration::ZERO));
    r.succeed();
    for i in 0..120 {
        r.record_presented(Duration::from_micros(i));
    }
    assert_eq!(r.attempts, 1, "rapid frames must not reset budget");
    r.record_presented(Duration::from_secs(5));
    assert_eq!(r.attempts, 0);
}
#[test]
fn elapsed_time_without_enough_frames_is_not_recovery() {
    let mut r = Recovery::default();
    assert!(r.begin(Duration::ZERO));
    r.succeed();
    r.record_presented(Duration::ZERO);
    r.record_presented(Duration::from_secs(60));
    assert_eq!(r.attempts, 1);
}

#[test]
fn a_failure_breaks_the_sustained_success_window() {
    let mut r = Recovery::default();
    assert!(r.begin(Duration::ZERO));
    r.succeed();
    for i in 0..120 {
        r.record_presented(Duration::from_millis(i));
    }
    r.fail(Duration::from_secs(4));
    assert!(r.begin(Duration::from_secs(5)));
    r.succeed();
    r.record_presented(Duration::from_secs(9));
    assert_eq!(r.attempts, 2);
}

#[test]
fn unhealthy_or_exhausted_devices_cannot_report_themselves_healthy() {
    let mut r = Recovery::default();
    for seconds in [0, 2, 5] {
        assert!(r.begin(Duration::from_secs(seconds)));
        r.fail(Duration::from_secs(seconds));
    }
    for i in 0..200 {
        r.record_presented(Duration::from_secs(10 + i));
    }
    assert!(r.exhausted());
    assert_eq!(r.attempts, 3);
}
