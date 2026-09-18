/// Source-wiring guard is deliberately separate from native OS acceptance.
/// Carbon 5 is key press; 6 is key release. A panic must not wait for key-up.
#[test]
fn native_panic_registration_uses_the_press_event() {
    let source = include_str!("../src/native/hotkey.rs");
    assert!(
        source.contains("kind: 5,"),
        "panic listener is not wired to Carbon key-press event 5"
    );
    assert!(
        !source.contains("kind: 6,"),
        "panic listener is wired to key release"
    );
}
