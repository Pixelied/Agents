# Branch registry — post-cleanup 2026-08-24

This is the authoritative branch-usage registry after the repository project split and physical branch prune.

The rule is intentionally simple: **new work starts from `main` for workspace/tooling work or from the appropriate `project/...` branch for project work.** The other surviving branches are recovery refs or temporary compatibility/dependency refs for already-open PRs.

## 1. Normal bases — use these

### Workspace

- `main` — coordination/workspace source of truth. Ordinary project implementation trees do not belong here.

### Canonical projects

- `project/crystal-anchor-combat-optimizer-26-1-2`
- `project/fallen-knight-26-1-2`
- `project/hypershot`
- `project/medusa-26-1-2`
- `project/pearl-catcher-26-1-2`
- `project/predictive-survival-26-1-2`
- `project/spear-client-26-1-2`

Each canonical project branch contains exactly one audited project implementation tree. Exact project-tree SHAs are recorded in `docs/protocols/project-branches.md`.

For independent work, do not branch from another project's feature/fix/CI branch. Start from the canonical project branch. Stack on another branch only when there is a real, explicit unmerged dependency.

## 2. Recovery and compatibility refs — do not use for normal new work

- `archive/pre-branch-prune-2026-08-24` — recovery archive for all 67 physically deleted branch heads. Archive tip created by the prune: `256ff367e2f5f2382dee4d6fa23c322005d5e4b4`.
- `backup/pre-project-split-2026-08-24` — immutable rollback snapshot of the old mixed `main` at `5a64ea68509f08742995220cbd80319e19517d63`.
- `legacy/mixed-main-2026-08-24` — compatibility base for still-open PRs created before the project split. Never start new work here.

The archive branch contains `docs/protocols/branch-prune-archive-2026-08-24.json` with exact deleted branch→SHA mappings.

## 3. Live PR/dependency refs — preserve until their existing PR chain is resolved

These nine branches survived only because an existing open PR or stacked dependency still requires them. They are not canonical bases for new independent work.

### SpeedBridge legacy chain

- `design/speedbridge-breezily-silent-aim` — PR #4 head.
- `ci/speedbridge-breezily-milestone-1-5` — PR #5 head.
- `ci/speedbridge-26-2-hook-inspect` — PR #10 head.

SpeedBridge remains a legacy exception because the repository still does not contain an honestly materialized complete canonical source tree for it. Do not manufacture a `project/speedbridge-*` branch from partial patch/CI artifacts.

### Fallen Knight / Medusa live repair PRs

- `fix/fallen-knight-playtest-v2` — PR #17 head. Its newest project tree is already preserved on `project/fallen-knight-26-1-2`.
- `fix/medusa-dungeon-rebuild` — PR #16 head. Its newest project tree is already preserved on `project/medusa-26-1-2`.

These refs remain for their existing PRs; future independent work returns to the canonical `project/...` branch.

### Crystal stacked chain

- `design/crystal-optimizer-v2` — base of PR #19.
- `fix/crystal-optimizer-v2-kickstart` — PR #19 head and PR #23 base.
- `feat/crystal-optimizer-v2-lethal-efficiency` — PR #23 head and PR #24 base.
- `feat/crystalbot-v3-world-class` — PR #24 head. Its newest project tree is already preserved on `project/crystal-anchor-combat-optimizer-26-1-2`.

Do not flatten or rewrite this active stack merely for cosmetic history cleanup. Once the current stack is deliberately resolved, new independent Crystal work starts from the canonical project branch.

## 4. Physical prune result

On 2026-08-24 the repository was reduced from **87 branches to 20**.

- 67 explicit superseded/history/staging/probe branches were archived by exact head SHA and then physically deleted.
- 0 deletion candidates were missing or skipped.
- No canonical branch was deleted.
- No current open PR head/base was deleted.
- The temporary physical-prune branch itself was deleted.

Full execution/recovery details are in `docs/protocols/branch-prune-manifest-2026-08-24.md` and in the archive JSON stored on `archive/pre-branch-prune-2026-08-24`.

## 5. Future branch rules

1. **Workspace/tooling:** branch from current `main`.
2. **Existing project:** branch from that project's current `project/<project-id>` branch.
3. **New independent project:** create `project/<project-id>` from clean current `main`, then add only that project's implementation and CI.
4. **Stacked work:** branch from another feature/fix branch only when an actual unmerged dependency requires it, and make that dependency explicit in the PR base.
5. **Temporary branches:** use only when necessary and remove them once their purpose is complete. Do not accumulate `tmp/`, `stage/`, recovery, or CI branches indefinitely.
6. **Completed PR branches:** after a PR/dependency chain is resolved and its desired project state is safely represented on the canonical project branch, remove the obsolete branch rather than leaving another permanent version behind.
7. **Never choose "newest" from names.** Compare actual project tree identity, ancestry, task/PR intent, and verification state.
8. **Never revive a deleted historical branch casually.** Use the recovery archive mapping and restore only the exact ref genuinely needed.

The existence of an active PR branch does not make it a normal base. `main` and the seven `project/...` branches are the only normal starting points in the current topology.
