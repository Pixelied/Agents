# Tasks

Each task is isolated under `tasks/<task-id>/` and contains:

- `task.json` — durable definition and declared scopes;
- `leases/` — deterministic exclusive scope leases;
- `events/` — append-only history;
- `handoffs/` — append-only agent transfers;
- `artifacts/` — task-specific outputs that are not source changes.

Create tasks with `python agentctl.py task-create`. Do not create ad hoc status files; use `python agentctl.py status` to derive current state.
