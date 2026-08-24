# Coordination protocol

## Core model

The workspace separates durable definitions from live coordination state.

- Agent profiles identify one concrete worker instance.
- Task definitions state the objective, acceptance criteria, and allowed scopes.
- Leases grant temporary exclusive ownership of one declared scope.
- Events preserve append-only history.
- Handoffs transfer context and an exact next action.
- Status is derived; no shared status document is edited by every worker.

The authoritative coordination branch is `main`. That is intentionally separate from the branch that owns a project's implementation.

## Coordination truth versus implementation ancestry

Agents always refresh coordination state from `main` before registration, claims, heartbeats, releases, events, handoffs, or agent/task-state changes.

Implementation work chooses its base independently:

- workspace/protocol/tooling work -> `main`;
- a new independent project -> new `project/<project-id>` from clean `main`;
- existing project work -> that project's `project/<project-id>`;
- genuinely dependent unmerged work -> the dependency branch, explicitly stacked.

A project worker therefore may read/write coordination on `main` while its implementation commits live on a different project branch. Do not solve that separation by merging unrelated project implementations into the project branch. See `docs/protocols/project-branches.md`.

## Git materialization semantics

The protocol is defined by durable records, not by the physical presence of every empty directory. Git does not track empty directories, so a clean checkout may legitimately omit an empty `leases/`, `handoffs/`, `artifacts/`, agent `inbox/`, agent `notes/`, or `projects/` directory. Coordination writers recreate parent directories when they write the first record.

Task `events/` are intentionally stricter: `task-create` immediately writes a `task_created` event, so a normally-created task should have tracked event history. Validation may reject a missing `events/` directory while accepting a missing empty `handoffs/` directory.

Do not add `.gitkeep` files or validation requirements solely to force optional runtime directories into repository history. Add a placeholder only when it has a separate human-navigation purpose.

## Why deterministic lease paths matter

A scope such as `src/parser` maps to an escaped deterministic filename such as `leases/src%2Fparser.json`. Competing claims target the same path. In one synchronized checkout, the second claim is rejected immediately. Across branches, Git exposes the collision when the claim commits are reconciled.

## Branch-safe operating patterns

### Shared coordination branch

Use `main` as the authoritative live-state ledger. Coordination writes use the latest file SHA or branch head as a precondition. A rejected write must refresh and retry after re-evaluating the state.

### Claim-first pull request

Create and merge a small PR containing registration and the lease before starting exclusive implementation. Implementation happens on the correct workspace or project branch only after the claim reaches `main`.

### Coordinator-mediated claims

One coordinator performs all lease writes. Workers request scopes through messages or handoffs and begin only after the coordinator confirms the claim.

## Visibility rule

An unmerged branch is private coordination state, not a global lock. Agents must not assume other workers can see it.

Similarly, a branch name is not canonical-version evidence. When recovering or consolidating project work, inspect actual tree/content identity, ancestry, PR purpose, and verification state before deciding which copy is newest.

## Scope design

Scopes should be independent enough that two agents can change them without editing the same files. Good scopes include `docs`, `tests/cli`, `src/parser`, and one project directory. Bad scopes include `everything`, `misc`, and overlapping paths such as `src` plus `src/parser` assigned concurrently.

Project branches reduce accidental filesystem overlap, but they do not replace leases. Two agents can still create conflicting changes to the same project on separate branches, so both branch ancestry and coordination ownership must be checked.

## Recovery

Expired leases can be reclaimed. Released leases remain as history and may be replaced by a later generation. Never reclaim an unexpired lease without explicit human or coordinator intervention.

For repository-history recovery, preserve an immutable backup before destructive topology migrations. Active PR heads must never be force-updated merely to make branch history look cleaner.
