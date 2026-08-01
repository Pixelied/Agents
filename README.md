# Agents

A provider-neutral, Git-native workspace for coordinating multiple AI agents without letting them stomp on the same files or lose context between sessions.


The repository provides:

- one universal operating manual in `AGENTS.md`;
- machine-readable agent identities and task definitions;
- exclusive, expiring leases for task scopes;
- append-only events and structured handoffs;
- a dependency-free Python CLI;
- validation, tests, schemas, templates, and CI;
- compatibility shims for Claude, Gemini, GitHub Copilot, and tools that natively read `AGENTS.md`.

## Start here

Agents must read [`AGENTS.md`](AGENTS.md) before doing anything. Humans can use the same workflow.

```bash
python agentctl.py init
python agentctl.py register --id local-agent-a1b2 --provider local --model unknown
python agentctl.py task-create \
  --id demo-task \
  --title "Demo task" \
  --created-by local-agent-a1b2 \
  --objective "Demonstrate the coordination workflow" \
  --scope docs
python agentctl.py claim --task demo-task --scope docs --agent local-agent-a1b2
python agentctl.py status --task demo-task
```

## Why this structure works

A single shared `status.md` becomes a merge-conflict magnet. This workspace instead uses deterministic lease files for exclusive ownership and uniquely named append-only files for events and handoffs. Agents can work independently while preserving a durable audit trail.

Git branches are still isolated, so a claim is only globally authoritative once it reaches the source-of-truth coordination history. The exact safe patterns are documented in `AGENTS.md` and `docs/protocols/coordination.md`.

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

## Requirements

Python 3.11 or newer. Runtime code uses only the Python standard library.

## Verify the workspace

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

## Design goal

This is deliberately not a full orchestration server. It is a strong coordination protocol that works with ordinary Git and GitHub, remains inspectable by humans, and can later be wrapped by bots or APIs without changing the on-disk contract.
