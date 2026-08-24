# Contributing

All contributors, human or automated, follow `AGENTS.md`.

## Choose the correct base first

This repository deliberately separates shared coordination state from project implementation state.

- Workspace/protocol/tooling change: branch from current `main`.
- New independent project: create `project/<project-id>` from current clean `main`; first implementation lands on that project branch.
- Existing project change: branch from the current `project/<project-id>` branch.
- Dependent unmerged work: stack on the dependency branch only intentionally, and make that dependency explicit in the pull request.

Do not branch an independent project from another project's feature branch. Do not merge another project's implementation merely to obtain newer shared workspace files. Coordination reads and writes still synchronize against `main` even when implementation work lives on a project branch.

See `docs/protocols/project-branches.md`.

## Change flow

1. Synchronize coordination state with `main`.
2. Select the correct implementation base using the rules above.
3. Register a unique agent or contributor instance when participating in coordinated work.
4. Inspect the task and claim the smallest required scope.
5. Make focused changes on a dedicated branch.
6. Add or update tests before changing behavior.
7. Run the relevant project gate plus the workspace validator when workspace state is changed.
8. Record a useful handoff when another worker will continue.
9. Release the scope after the work is merged, handed off, blocked, or abandoned.

## Pull requests

A pull request should explain the problem, approach, affected scopes, verification, concurrency implications, base branch, and any protocol migration.

Project PRs should normally target their `project/<project-id>` base. Intentionally stacked PRs target their dependency branch until that dependency is resolved. Workspace/protocol PRs target `main`.

Do not mix unrelated project cleanup with protocol changes.

Changes to `AGENTS.md`, `.agent-workspace.json`, `schemas/`, lease semantics, validation behavior, or the canonical branch model are protocol/workspace changes and require matching documentation and appropriate verification.

## Commit hygiene

Prefer small commits that leave the workspace valid. Never rewrite append-only task events or handoffs. Do not commit secrets, generated caches, virtual environments, or local agent credentials.
