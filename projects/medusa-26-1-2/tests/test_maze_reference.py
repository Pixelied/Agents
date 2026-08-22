import unittest

from scripts.maze_reference import (
    ROWS,
    COLS,
    MIN_DELTA,
    MIN_ROUTE,
    changed_edges,
    generate_initial,
    propose_shift,
    validate,
)


class MazeReferenceContract(unittest.TestCase):
    def test_initial_and_many_shifts_are_connected_and_nontrivial(self):
        for seed in range(100):
            current = generate_initial(seed)
            check = validate(current)
            self.assertEqual(check.reachable_count, ROWS * COLS)
            self.assertGreaterEqual(check.sanctum_distance, MIN_ROUTE)
            for cycle in range(20):
                nxt = propose_shift(current, seed * 1000 + cycle)
                check = validate(nxt)
                self.assertEqual(check.reachable_count, ROWS * COLS)
                self.assertGreaterEqual(check.sanctum_distance, MIN_ROUTE)
                self.assertGreaterEqual(len(changed_edges(current, nxt)), MIN_DELTA)
                current = nxt

    def test_bounded_failure_keeps_previous_valid_state(self):
        current = generate_initial(7)
        nxt = propose_shift(current, 99, max_attempts=0)
        self.assertEqual(nxt, current)

    def test_seeds_do_not_collapse_to_a_small_preset_set(self):
        signatures = {generate_initial(seed).signature() for seed in range(64)}
        self.assertGreater(len(signatures), 56)


if __name__ == "__main__":
    unittest.main()
