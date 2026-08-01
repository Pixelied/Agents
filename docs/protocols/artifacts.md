# Artifact storage

The coordination repository is an index and audit trail, not a general-purpose binary warehouse.

## Keep in Git

Commit small, reviewable coordination evidence:

- task metadata, events, leases, and handoffs;
- Markdown reports and concise machine-readable summaries;
- checksums, artifact IDs, workflow run IDs, repository names, commit SHAs, and expiry dates;
- small text fixtures that are necessary to test the coordination system itself.

## Keep outside Git

Use a source repository commit, GitHub Actions artifact, release asset, package registry, or approved object store for:

- complete source-tree copies from another repository;
- generated binaries, JARs, ZIPs, logs, coverage bundles, screenshots, or build directories;
- large test corpora and temporary debugging captures.

Prefer an immutable reference such as `owner/repository@commit-sha`. Record a SHA-256 digest when the referenced object can change or expire.

## Base64 is not storage compression

Base64 increases binary payload size and produces poor diffs. Do not split archives into Base64 files merely to fit them into Git. A task may do so only when a human explicitly requires a self-contained emergency snapshot and approves the repository-size impact.

## Artifact manifest

A durable report should record, when applicable:

```json
{
  "source": "owner/repository@commit-sha",
  "workflow_run_id": 123456,
  "artifact_id": 789012,
  "sha256": "...",
  "expires_at": "2026-08-15T15:01:22Z"
}
```

Never rewrite historical task artifacts simply to adopt this guidance. Apply it to new work and migrations that have their own declared scope.
