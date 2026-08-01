# Troubleshooting

## “Scope is actively leased”

Read the lease owner and expiry. Choose another scope, request a handoff, or wait until the owner releases it. Do not overwrite an active lease.

## Lease expired while I was working

Stop editing. Refresh repository state and reclaim the scope. If another agent already reclaimed it, hand off your partial work rather than racing them.

## Two branches contain different claims

Neither private branch is globally authoritative. Reconcile against the source-of-truth branch. The first accepted deterministic lease write wins; the losing agent must re-plan.

## Validation reports an unknown agent

The lease refers to an agent profile not present in the same history. Merge or recreate the missing registration before the lease, or release the invalid lease through an explicit repair change.

## An agent disappeared

Wait for the lease to expire, inspect its events and branch, then reclaim the scope with a new generation. Record recovery details in an event.
