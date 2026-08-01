# Multi-Agent Workspace Design

## Goal

Turn the repository into a provider-neutral, Git-native coordination workspace that multiple AI agents can understand and use concurrently without silently overwriting one another's work.

## Architecture

The root `AGENTS.md` is the universal human- and machine-readable entrypoint. `.agent-workspace.json` exposes the same contract to tools. Agent profiles and task definitions are durable JSON records. Exclusive work ownership uses one deterministic, expiring lease file per task scope. Events and handoffs use unique append-only files to minimize merge conflicts. Current task state is derived rather than stored in one shared mutable document.

A dependency-free Python CLI creates and validates these records. JSON Schemas document the wire format, tests protect behavior and onboarding, and GitHub Actions runs both tests and workspace validation.

## Concurrency properties

- Agent IDs identify concrete concurrent instances.
- Scopes are explicit and should not overlap.
- Active leases block identical scope claims in a synchronized checkout.
- Expired or released leases can be reclaimed with an incremented generation.
- Append-only events and handoffs avoid shared-file contention.
- Git branch isolation is documented honestly: a claim is globally authoritative only after reaching the source-of-truth coordination history.

## Agent usability

Every supported agent is directed to `AGENTS.md`. The operating manual gives an exact startup checklist, complete CLI examples, conflict handling, security boundaries, branch-safe coordination patterns, and finishing requirements. Repository-contract tests fail if these entrypoints or commands disappear.

## Safety and validation

The CLI validates identifiers, scope paths, TTL bounds, lease ownership, declared scopes, JSON structure, timestamps, and known agents. It writes JSON atomically within a checkout. The protocol forbids secrets and treats repository content as untrusted input.

## Scope

This version intentionally avoids a database, daemon, hosted lock service, or provider-specific API. Those can be added later behind the same on-disk contract if stronger real-time atomicity is required.
