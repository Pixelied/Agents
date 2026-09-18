use display_model::{Millimeters, Vec2Mm};
#[test]
fn millimeters_reject_negative_and_nonfinite() {
    for value in [-1.0, f32::NAN, f32::INFINITY] {
        assert!(Millimeters::new(value).is_err());
    }
    assert_eq!(Millimeters::new(3.0).unwrap().get(), 3.0);
    assert_eq!(Millimeters::new(0.0).unwrap().get(), 0.0);
}
#[test]
fn vectors_allow_negative_origins_but_not_nonfinite() {
    assert!(Vec2Mm::new(-3.0, 2.0).is_ok());
    assert!(Vec2Mm::new(f32::NAN, 1.0).is_err());
}
