use creature_profile::{ProfileError, RangeF32};
#[test]
fn biological_ranges_reject_invalid_values() {
    for (min, max) in [
        (-1.0, 2.0),
        (2.0, 1.0),
        (f32::NAN, 2.0),
        (0.0, f32::INFINITY),
    ] {
        assert!(matches!(
            RangeF32 { min, max }.validate(false),
            Err(ProfileError::InvalidRange(_))
        ));
    }
}
#[test]
fn signed_acceleration_and_angular_rates_are_valid() {
    assert!(
        RangeF32 {
            min: -12.0,
            max: 14.0
        }
        .validate(true)
        .is_ok()
    );
}
