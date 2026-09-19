use display_model::DisplayId;
use glam::Vec2;
use simulation::spatial::SpatialHash;

// A duplicate slot used to link the bucket back to itself. Use a finite visit
// budget for the regression so the unfixed implementation fails, not hangs.
fn nearby(grid: &SpatialHash, radius: f32) -> Vec<usize> {
    let mut out = Vec::new();
    grid.visit(DisplayId(1), Vec2::ZERO, radius, 32, |slot, _| {
        out.push(slot);
    });
    out.sort_unstable();
    out
}

#[test]
fn duplicate_active_slots_do_not_create_bucket_cycles() {
    let mut grid = SpatialHash::new(4, 2.);
    grid.rebuild(
        &[Vec2::ZERO, Vec2::X],
        &[DisplayId(1), DisplayId(1)],
        &[0, 1, 0],
    );
    assert_eq!(nearby(&grid, 2.), vec![0, 1]);
}

#[test]
fn wide_queries_do_not_repeat_duplicate_active_slots() {
    let mut grid = SpatialHash::new(4, 2.);
    grid.rebuild(
        &[Vec2::ZERO, Vec2::X],
        &[DisplayId(1), DisplayId(1)],
        &[0, 1, 0, 1],
    );
    assert_eq!(nearby(&grid, 200.), vec![0, 1]);
}

#[test]
fn membership_is_cleared_between_rebuilds_and_empty_frames() {
    let mut grid = SpatialHash::new(4, 2.);
    let positions = [Vec2::ZERO, Vec2::X];
    let displays = [DisplayId(1), DisplayId(1)];
    grid.rebuild(&positions, &displays, &[0, 1, 0]);
    grid.rebuild(&positions, &displays, &[1]);
    assert_eq!(nearby(&grid, 2.), vec![1]);
    grid.rebuild(&positions, &displays, &[]);
    assert!(nearby(&grid, 2.).is_empty());
    grid.rebuild(&positions, &displays, &[1, 0, 1]);
    assert_eq!(nearby(&grid, 2.), vec![0, 1]);
}

#[test]
fn invalid_slots_and_duplicate_valid_slots_are_independent() {
    let mut grid = SpatialHash::new(4, 2.);
    grid.rebuild(
        &[Vec2::ZERO, Vec2::X, Vec2::splat(f32::NAN)],
        &[DisplayId(1), DisplayId(1), DisplayId(1)],
        &[usize::MAX, 2, 0, 3, 1, 0, 1],
    );
    assert_eq!(nearby(&grid, 2.), vec![0, 1]);
}
