# Task and agent lifecycle

1. **Register** — create one unique identity for the exact agent session or durable worker.
2. **Create** — define objective, scopes, acceptance criteria, priority, and creator.
3. **Inspect** — read task metadata, recent events, handoffs, active leases, and agent states.
4. **Claim** — acquire the smallest required scope for a bounded TTL.
5. **Execute** — work on an isolated branch and record meaningful events.
6. **Heartbeat** — extend the lease while active work continues.
7. **Verify** — run task-specific checks, the test suite, and workspace validation.
8. **Handoff or finish** — transfer exact context when another agent continues.
9. **Release** — release every owned scope with a useful reason.
10. **Set agent state** — after all leases are released, mark a one-shot agent `offline` or permanently `retired`.

A task's `status` field is its initial durable classification. Operational task state is derived from events and leases.

Agent profiles have a declared state: `available`, `busy`, `offline`, or `retired`. `agent-list` also exposes an effective state. An agent with an active lease is effectively `busy`; a declared `available` agent with no active lease is effectively `idle`. `idle` is derived and is not written to the profile.

An agent cannot become `offline` or `retired` while it owns an unexpired active lease. A retired identity is terminal and must never be reactivated or reused.
