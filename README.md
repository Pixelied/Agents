# Agents

A provider-neutral coordination system that lets multiple AI agents safely discover, claim, execute, hand off, and validate work through Git and GitHub.

`main` is deliberately the **coordination workspace**, not a dumping ground for every project implementation. Real project code lives on dedicated long-lived `project/<project-id>` branches so one project's branch does not inherit unrelated project trees merely because they happened to be merged into `main` earlier.

The repository provides:

- one universal operating manual in `AGENTS.md`;
- machine-readable agent identities and task definitions;
- exclusive, expiring leases for task scopes;
- append-only events and structured handoffs;
- explicit and derived agent lifecycle states;
- a dependency-free Python CLI;
- validation, tests, schemas, templates, and shared CI;
- canonical project-base branches for project implementation work;
- compatibility shims for Claude, Gemini, GitHub Copilot, and tools that natively read `AGENTS.md`.

## Start here

Agents must read [`AGENTS.md`](AGENTS.md) before doing anything.

```bash
python agentctl.py init
python agentctl.py register --id local-agent-a1b2 --provider local --model unknown
python agentctl.py task-list
python agentctl.py agent-list
```

After creating or selecting a task, claim only the smallest declared scope. When work is complete, release every lease and close the session explicitly:

```bash
python agentctl.py agent-state \
  --agent local-agent-a1b2 \
  --state offline \
  --reason "Session finished and all scopes released"
```

`agent-list` reports both the declared state and an effective state derived from active leases. Active workers appear `busy`; available agents with no active lease appear `idle`.

## Branch model

There are two different kinds of source-of-truth in this workspace:

- **Coordination source of truth:** `main`. Agent identities, tasks, leases, events, handoffs, schemas, shared tooling, and shared protocol documentation are synchronized from `main`.
- **Project implementation source of truth:** `project/<project-id>`. Existing project work branches from that project's canonical project branch.

Use this rule:

```text
Workspace/protocol change  -> branch from main
New independent project    -> create project/<project-id> from clean main
Existing project change    -> branch from project/<project-id>
Dependent unmerged change  -> stack intentionally on its dependency branch
```

A project feature branch still contains the shared coordination workspace because it inherits clean `main`, but it should contain only **its own** ordinary implementation tree under `projects/`.

Do not branch an independent project from another project's feature branch. Do not merge another project's implementation just to obtain newer workspace tooling. See [`docs/protocols/project-branches.md`](docs/protocols/project-branches.md).

## Why this structure works

A shared mutable status document becomes a merge-conflict magnet. This workspace instead uses deterministic lease files for exclusive ownership and uniquely named append-only events and handoffs. Git branches are isolated, so claims must reach the source-of-truth coordination history before exclusive work begins.

Separating project implementations from `main` also prevents Git's snapshot model from making every feature branch appear to contain every unrelated project.

## Artifact policy

Keep small reports, checksums, IDs, immutable source references, and coordination evidence in Git. Put large source copies, binaries, logs, and generated bundles in their original repository, GitHub Actions artifacts, releases, or approved object storage. Base64 is not a substitute for artifact storage. Historical emergency snapshots may remain when removing them would destroy evidence. See [`docs/protocols/artifacts.md`](docs/protocols/artifacts.md).

## Repository map on `main`

```text
AGENTS.md                      Universal instructions every agent reads first
.agent-workspace.json          Machine-readable workspace manifest
agentctl.py                    CLI entrypoint
src/agent_workspace/           Coordination implementation
agents/<agent-id>/             Agent identity and agent-owned runtime state
tasks/<task-id>/               Task metadata, leases, events, handoffs, artifacts
templates/                     Copyable machine-readable examples
schemas/                       JSON Schema contracts
docs/protocols/                Detailed operating rules and project branch registry
examples/                      Worked workflow examples
tests/                         Workspace behavioral and repository-contract tests
.github/workflows/             Shared workspace CI; project CI lives on project branches
```

A normal `main` checkout may have no `projects/` directory at all. Git does not track empty directories. Canonical project branches add one project tree at `projects/<project-id>/` plus that project's CI workflow.

Optional runtime folders such as an empty task `handoffs/` directory or agent `inbox/` may likewise be absent in a clean checkout until the first record is written. See [`docs/protocols/coordination.md`](docs/protocols/coordination.md).

## Requirements and verification

Python 3.11 or newer. Runtime code uses only the Python standard library.

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

These commands validate the shared coordination workspace. Individual canonical project branches can add their own language-, game-, or platform-specific CI gate without putting that project workflow back on `main`.
