# Agents

A provider-neutral coordination system that lets multiple AI agents safely discover, claim, execute, hand off, and validate work through Git and GitHub.

It is also meant to be a clean place to keep the actual code those agents are working on. One or more real codebases can live alongside the coordination layer as clearly separated, first-class projects, so the repository can act as both the workspace for the work and the system that keeps that work organized without turning into a pile of random files.

The repository provides:

- one universal operating manual in `AGENTS.md`;
- machine-readable agent identities and task definitions;
- exclusive, expiring leases for task scopes;
- append-only events and structured handoffs;
- explicit and derived agent lifecycle states;
- a dependency-free Python CLI;
- validation, tests, schemas, templates, and CI;
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

## Why this structure works

A shared mutable status document becomes a merge-conflict magnet. This workspace instead uses deterministic lease files for exclusive ownership and uniquely named append-only events and handoffs. Git branches are isolated, so claims must reach the source-of-truth coordination history before exclusive work begins.

## Artifact policy

First-class codebases being actively developed can live directly in this repository; keep them clearly separated from coordination state and from each other. Keep small reports, checksums, IDs, and immutable source references in Git. Put large source copies, binaries, logs, and generated bundles in their original repository, GitHub Actions artifacts, releases, or approved object storage. Base64 is not a substitute for artifact storage. See [`docs/protocols/artifacts.md`](docs/protocols/artifacts.md).

## Repository map

```text
AGENTS.md                      Universal instructions every agent reads first
.agent-workspace.json          Machine-readable workspace manifest
agentctl.py                    CLI entrypoint
src/agent_workspace/           Coordination implementation
agents/<agent-id>/             Agent identity, inbox, and private scratch notes
tasks/<task-id>/               Task metadata, leases, events, handoffs, artifacts
templates/                     Copyable machine-readable examples
schemas/                       JSON Schema contracts
docs/protocols/                Detailed operating rules
examples/                      Worked workflow examples
tests/                         Behavioral and repository-contract tests
.github/workflows/validate.yml CI validation
```

## Requirements and verification

Python 3.11 or newer. Runtime code uses only the Python standard library.

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```
