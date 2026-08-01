# Multi-Agent Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a provider-neutral, Git-native workspace that teaches agents how to coordinate and prevents conflicting task-scope ownership.

**Architecture:** Use deterministic expiring lease files for exclusive task scopes and unique append-only files for events and handoffs. Expose the protocol through a standard-library Python CLI, a universal `AGENTS.md` entrypoint, machine-readable manifests and schemas, tests, and GitHub Actions.

**Tech Stack:** Python 3.11 standard library, JSON, JSON Schema Draft 2020-12, Markdown, GitHub Actions.

## Global Constraints

- Runtime code has no third-party dependencies.
- Every provider follows the same `AGENTS.md` protocol.
- Agent and task IDs are lowercase stable identifiers.
- Scope paths reject absolute paths and traversal.
- Leases expire and use deterministic task/scope paths.
- Events and handoffs are append-only.
- Git branch visibility limitations are documented explicitly.

---

### Task 1: Coordination core

**Files:**
- Create: `src/agent_workspace/core.py`
- Create: `src/agent_workspace/__init__.py`
- Test: `tests/test_workspace.py`

**Interfaces:**
- Produces: `Workspace` methods for initialization, registration, task creation, claims, heartbeats, releases, events, handoffs, status, and validation.

- [x] Write failing unit tests for every coordination behavior.
- [x] Run the tests and confirm failures are caused by the missing implementation.
- [x] Implement the minimum standard-library coordination core.
- [x] Run the unit tests and confirm all coordination tests pass.

### Task 2: Command-line interface

**Files:**
- Create: `agentctl.py`
- Create: `src/agent_workspace/cli.py`
- Test: `tests/test_cli.py`

**Interfaces:**
- Consumes: `Workspace` from Task 1.
- Produces: stable commands `init`, `register`, `task-create`, `claim`, `heartbeat`, `event`, `handoff`, `status`, `release`, and `validate`.

- [x] Write failing end-to-end CLI tests for a complete workflow and a conflicting claim.
- [x] Run the tests and confirm the CLI entrypoint is missing.
- [x] Implement argument parsing, JSON output, and nonzero error codes.
- [x] Run CLI and core tests until they pass.

### Task 3: Agent onboarding contract

**Files:**
- Create: `AGENTS.md`
- Replace: `README.md`
- Create: `.agent-workspace.json`
- Create: `CLAUDE.md`
- Create: `GEMINI.md`
- Create: `.github/copilot-instructions.md`
- Test: `tests/test_repository_contract.py`

**Interfaces:**
- Produces: one authoritative agent entrypoint, provider shims, and a machine-readable workspace locator.

- [x] Write failing tests requiring every startup and finishing command.
- [x] Confirm the tests fail because the entrypoints do not exist.
- [x] Write the complete operating manual and provider shims.
- [x] Verify the contract tests pass.

### Task 4: Protocol contracts and examples

**Files:**
- Create: `schemas/*.schema.json`
- Create: `templates/*.json`
- Create: `docs/protocols/*.md`
- Create: `examples/multi-agent-workflow.md`
- Create: `CONTRIBUTING.md`

**Interfaces:**
- Consumes: record formats and CLI behavior from Tasks 1 and 2.
- Produces: inspectable schemas, copyable examples, and detailed concurrency/security guidance.

- [x] Add JSON Schemas for agents, tasks, leases, events, and handoffs.
- [x] Add valid example records for every schema.
- [x] Document lifecycle, leases, handoffs, Git concurrency, security, and recovery.
- [x] Add one complete multi-agent workflow example.

### Task 5: Continuous verification

**Files:**
- Create: `.github/workflows/validate.yml`
- Create: `.github/pull_request_template.md`
- Create: `.github/ISSUE_TEMPLATE/task.yml`
- Create: `Makefile`
- Create: `.gitignore`

**Interfaces:**
- Consumes: tests and `agentctl.py validate`.
- Produces: automatic pull-request checks and consistent contributor prompts.

- [x] Configure Python 3.11 CI.
- [x] Run unit, CLI, and repository-contract tests in CI.
- [x] Run workspace validation in CI.
- [x] Add PR and task templates that require scopes, verification, and concurrency impact.

### Task 6: Final verification

- [x] Run `python -m unittest discover -s tests -v`.
- [x] Run `python agentctl.py validate`.
- [x] Compile all Python files with `python -m compileall -q agentctl.py src tests`.
- [x] Inspect the final tree for caches, secrets, malformed JSON, and incomplete documentation.
- [ ] Commit the complete tree on `feat/multi-agent-workspace` and open a pull request.
