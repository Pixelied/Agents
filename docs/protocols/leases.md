# Ownership and leases

A lease grants one agent exclusive write ownership of one declared task scope.

## Lease properties

- `owner` identifies the registered agent instance.
- `scope` matches a scope declared in `task.json`.
- `claimed_at` records original acquisition time.
- `heartbeat_at` records the latest renewal.
- `expires_at` makes abandoned work recoverable.
- `generation` increments when a released or expired lease is reclaimed.
- `state` is `active` or `released`.

## TTL rules

TTL must be between 5 minutes and 24 hours. Choose a realistic duration and heartbeat rather than taking a day-long lease for a five-minute edit.

## Reclaim rules

A different agent may reclaim only when the existing lease is released or expired. The new claim replaces the deterministic scope file and increments its generation. Important context from the previous worker should remain in events or handoffs.

## Overlapping scopes

The CLI prevents identical scope collisions, not semantic overlap. Task creators must avoid declaring scopes that overlap. If `src` and `src/parser` both exist, a coordinator must not assign them concurrently.
