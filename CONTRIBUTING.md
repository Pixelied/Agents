# Contributing

All contributors, human or automated, follow `AGENTS.md`.

## Change flow

1. Synchronize with the source-of-truth branch.
2. Register a unique agent or contributor instance when participating in coordinated work.
3. Inspect the task and claim the smallest required scope.
4. Make focused changes on a dedicated branch.
5. Add or update tests before changing behavior.
6. Run the full test suite and workspace validator.
7. Record a useful handoff when another worker will continue.
8. Release the scope after the work is merged, handed off, blocked, or abandoned.

## Pull requests

A pull request should explain the problem, approach, affected scopes, verification, concurrency implications, and any protocol migration. Do not mix unrelated cleanup with protocol changes.

Changes to `AGENTS.md`, `.agent-workspace.json`, `schemas/`, lease semantics, or validation behavior are protocol changes and require matching tests and documentation.

## Commit hygiene

Prefer small commits that leave the workspace valid. Never rewrite append-only task events or handoffs. Do not commit secrets, generated caches, virtual environments, or local agent credentials.
