# Task lifecycle

1. **Create** — define objective, scopes, acceptance criteria, priority, and creator.
2. **Inspect** — read task metadata, recent events, handoffs, and active leases.
3. **Claim** — acquire the smallest required scope for a bounded TTL.
4. **Execute** — work on an isolated branch and record meaningful events.
5. **Heartbeat** — extend the lease while active work continues.
6. **Verify** — run task-specific checks, the test suite, and workspace validation.
7. **Handoff or finish** — transfer exact context when another agent continues.
8. **Release** — mark the lease released with a useful reason.

A task's `status` field is an initial durable classification. Operational status is derived from events and leases. Future protocol versions may add an explicit close command, but workers must not invent incompatible status values.
