# Coordination protocol

## Core model

The workspace separates durable definitions from live coordination state.

- Agent profiles identify one concrete worker instance.
- Task definitions state the objective, acceptance criteria, and allowed scopes.
- Leases grant temporary exclusive ownership of one declared scope.
- Events preserve append-only history.
- Handoffs transfer context and an exact next action.
- Status is derived; no shared status document is edited by every worker.

## Git materialization semantics

The protocol is defined by durable records, not by the physical presence of every empty directory. Git does not track empty directories, so a clean checkout may legitimately omit an empty `leases/`, `handoffs/`, `artifacts/`, agent `inbox/`, or agent `notes/` directory. Coordination writers recreate parent directories when they write the first record.

Task `events/` are intentionally stricter: `task-create` immediately writes a `task_created` event, so a normally-created task should have tracked event history. Validation may reject a missing `events/` directory while accepting a missing empty `handoffs/` directory.

Do not add `.gitkeep` files or validation requirements solely to force optional runtime directories into repository history. Add a placeholder only when it has a separate human-navigation purpose.

## Why deterministic lease paths matter

A scope such as `src/parser` maps to an escaped deterministic filename such as `leases/src%2Fparser.json`. Competing claims target the same path. In one synchronized checkout, the second claim is rejected immediately. Across branches, Git exposes the collision when the claim commits are reconciled.

## Branch-safe operating patterns

### Shared coordination branch

Use a dedicated branch as the authoritative live-state ledger. Coordination writes use the latest file SHA or branch head as a precondition. A rejected write must refresh and retry after re-evaluating the state.

### Claim-first pull request

Create and merge a small PR containing registration and the lease before starting exclusive implementation. Implementation happens on a separate branch based on the merged claim.

### Coordinator-mediated claims

One coordinator performs all lease writes. Workers request scopes through messages or handoffs and begin only after the coordinator confirms the claim.

## Visibility rule

An unmerged branch is private coordination state, not a global lock. Agents must not assume other workers can see it.

## Scope design

Scopes should be independent enough that two agents can change them without editing the same files. Good scopes include `docs`, `tests/cli`, `src/parser`, and `release-notes`. Bad scopes include `everything`, `misc`, and overlapping paths such as `src` plus `src/parser` assigned concurrently.

## Recovery

Expired leases can be reclaimed. Released leases remain as history and may be replaced by a later generation. Never reclaim an unexpired lease without explicit human or coordinator intervention.
