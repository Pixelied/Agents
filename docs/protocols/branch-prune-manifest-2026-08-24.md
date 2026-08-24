# Physical branch prune manifest — 2026-08-24

Status: **pre-prune audited manifest**

Audit cutoff: `2026-08-24T12:18:00Z`

Archive ref to create before deletion: `archive/pre-branch-prune-2026-08-24`

This manifest is intentionally explicit. The pruning workflow may delete only branches listed under **Deletion candidates**. It must dynamically protect every current open PR head/base branch, and it must abort before the first deletion if a listed candidate moved after the audit cutoff. The single exception is the cleanup branch created by this pruning task itself; that branch is expected to contain post-cutoff commits and is deleted only after PR #35 has merged/closed.

## Canonical and safety refs — never delete in this pass

```text
main
backup/pre-project-split-2026-08-24
legacy/mixed-main-2026-08-24
project/crystal-anchor-combat-optimizer-26-1-2
project/fallen-knight-26-1-2
project/hypershot
project/medusa-26-1-2
project/pearl-catcher-26-1-2
project/predictive-survival-26-1-2
project/spear-client-26-1-2
```

## Open PR/dependency refs at audit time — protect dynamically

Open PRs were re-read immediately before this manifest was written.

```text
PR #4  base legacy/mixed-main-2026-08-24                  head design/speedbridge-breezily-silent-aim
PR #5  base legacy/mixed-main-2026-08-24                  head ci/speedbridge-breezily-milestone-1-5
PR #10 base legacy/mixed-main-2026-08-24                  head ci/speedbridge-26-2-hook-inspect
PR #16 base legacy/mixed-main-2026-08-24                  head fix/medusa-dungeon-rebuild
PR #17 base legacy/mixed-main-2026-08-24                  head fix/fallen-knight-playtest-v2
PR #19 base design/crystal-optimizer-v2                   head fix/crystal-optimizer-v2-kickstart
PR #23 base fix/crystal-optimizer-v2-kickstart            head feat/crystal-optimizer-v2-lethal-efficiency
PR #24 base feat/crystal-optimizer-v2-lethal-efficiency   head feat/crystalbot-v3-world-class
```

Unique non-safety live PR/dependency refs:

```text
ci/speedbridge-26-2-hook-inspect
ci/speedbridge-breezily-milestone-1-5
design/crystal-optimizer-v2
design/speedbridge-breezily-silent-aim
feat/crystal-optimizer-v2-lethal-efficiency
feat/crystalbot-v3-world-class
fix/crystal-optimizer-v2-kickstart
fix/fallen-knight-playtest-v2
fix/medusa-dungeon-rebuild
```

## Deletion candidates

Exactly 67 audited branch names:

```text
backup/main-2026-08-01
backup/predictive-survival-hardening-6c12750
backup/weekly-2026-08-02
backup/weekly-2026-08-23
ci/predictive-survival-26-1-2-hardening
ci/predictive-survival-hardening-publish-v15
ci/predictive-survival-hardening-recovery-check
ci/predictive-survival-hardening-verify
ci/speedbridge-assist-1.1.0
cleanup/project-boundary-migration
cleanup/physical-branch-prune-2026-08-24
coord/hypershot-26-2-7c4e
coord/pearl-catcher-hardening-claim
coord/predictive-survival-contingency-closeout
coord/predictive-survival-review-7c4d-claim
coord/release-predictive-survival-review-lease
design/predictive-survival-26-1-2
feat/fallen-knight-26-1-2
feat/hypershot-26-2-production
feat/medusa-26-1-2
feat/multi-agent-workspace
feat/predictive-survival-26-1-2-sol
feat/predictive-survival-26-1-2
feat/predictive-survival-contingency-planner
feat/spear-client-26-1-2
feature/hypershot-26-2
fix/fallen-knight-playtest
fix/fallen-knight-playtest-v3
fix/pearl-catcher-26-1-2-hardening
fix/post-first-run-hardening
fix/predictive-survival-26-1-2-hardening
fix/predictive-survival-26-1-2-pristine
fix/predictive-survival-review-hardening
package/crystal-anchor-combat-optimizer-26-1-2
plan/predictive-survival-26-1-2
release/hypershot-0.2.0-beta.1
stage/medusa-full-forceload-fix
stage/medusa-initial-validation-fix
stage/medusa-parser-and-maze-diagnostics
stage/medusa-shifting-task2
stage/medusa-shifting-task3
stage/medusa-shifting-task4
stage/medusa-shifting-task5
stage/medusa-shifting-task6
stage/medusa-shifting-task7-fix
stage/medusa-shifting-task7
stage/medusa-shifting-task8
stage/medusa-shifting-task9
stage/medusa-shifting-task10-fix
stage/medusa-shifting-task10
tmp/cleanup-probe
tmp/delete-me
tmp/ignore-this
tmp/last-probe
tmp/predictive-survival-baked-latest
tmp/predictive-survival-pristine-assemble
tmp/predictive-survival-pristine-assemble-2
tmp/ps-final-final
tmp/ps-pristine
tmp/ps-pristine-tree
tmp/what
tmp-noop-should-not-create
work/crystal-anchor-combat-optimizer-26-1-2
work/crystal-optimizer-v2
work/medusa-build-staging
work/medusa-maze-constructive-proposal
work/medusa-shifting-maze-inline
```

## Execution invariants

1. A branch created after this audit but absent from the explicit candidate list is never deleted.
2. If a candidate becomes an open PR head/base before execution, the workflow aborts before any deletion.
3. If a candidate's head commit is newer than the audit cutoff, the workflow aborts before any deletion, except `cleanup/physical-branch-prune-2026-08-24`, which is owned by this cleanup task and is expected to move while PR #35 is prepared.
4. Any unexpired active lease owned by another agent aborts pruning before deletion.
5. Before the first DELETE request, the workflow creates and verifies `archive/pre-branch-prune-2026-08-24` whose ancestry retains every candidate head SHA that existed at execution time.
6. The archive tree contains `docs/protocols/branch-prune-archive-2026-08-24.json` with exact branch→SHA mappings.
7. The one-shot destructive workflow is removed from `main` immediately after post-prune verification.
