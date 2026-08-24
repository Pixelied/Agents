# Project-Boundary Repository Cleanup Design

**Date:** 2026-08-24
**Status:** Approved for execution by the user
**Task:** `clean-repository-project-boundaries`

## Goal

Turn `Pixelied/Agents` back into a clear coordination workspace: `main` owns the agent protocol, task history, shared tooling, and shared documentation, while each real project has one long-lived `project/<project-id>` branch containing that project's canonical implementation and project-specific CI. Preserve every newer implementation state and protect active pull requests while the history is migrated.

## Non-negotiable safety rules

1. Never choose a canonical project by branch name, suffix, or timestamp alone. Compare project tree identity, ancestry, PR purpose, and verified/active development state.
2. Never force-update an active project branch or PR head.
3. Preserve the complete pre-cleanup `main` at `backup/pre-project-split-2026-08-24` before removing project trees.
4. Before `main` loses project trees, retarget every still-open PR whose base is `main` to a legacy base with the same pre-cleanup content so its diff is not polluted by the split.
5. Build new project branches by transplanting only the selected project tree onto clean `main`; never merge an old mixed branch wholesale.
6. Keep the coordination history (`agents/`, `tasks/`, schemas, docs, CLI, tests) on `main`. Project branches inherit that clean workspace base but not unrelated project implementations.
7. A project-specific workflow lives on the corresponding project branch. Shared workspace validation/backup workflows remain on `main`.
8. A temporary/staging/recovery branch may be removed only when its tip is provably redundant (for example, an ancestor of a retained branch) and it is not an open PR head. If deletion tooling is unavailable, document it as superseded instead of pretending it was deleted.
9. Re-check moving active heads immediately before the canonical snapshot is created. If a head moved, use the newer project tree.
10. Run workspace validation before and after the split, and use project CI on every new canonical project branch where a runnable project workflow exists.

## Branch model after cleanup

### `main`

`main` is the source of truth for coordination state and shared workspace tooling. It must not contain ordinary project implementation directories under `projects/` or ordinary project-specific CI workflows.

Workspace/protocol work starts from `main`:

```text
main
  -> fix/workspace-...
  -> feat/workspace-...
  -> docs/workspace-...
```

### `project/<project-id>`

Each normal project gets one long-lived canonical base branch. It starts from the cleaned `main` and adds exactly one project implementation tree plus its own CI workflow.

Normal project work starts from the project base, not from `main`:

```text
main
  -> project/medusa-26-1-2
       -> fix/medusa-...
       -> feat/medusa-...

main
  -> project/predictive-survival-26-1-2
       -> feat/predictive-survival-...
```

Independent features for the same project should normally be siblings from the project base. Stacked branches are allowed only when the later work intentionally depends on unmerged earlier work; the PR base must explicitly reflect that dependency.

### Legacy active PR bases

Open PRs created under the old mixed-main architecture are not rewritten. They are retargeted to the immutable pre-split legacy branch and may finish there. New work must use the new project-base model.

## Canonical project-source inventory

This inventory records the newest project tree found during the cleanup audit. Tree SHA is the source-of-truth identity for the implementation snapshot.

| Project | Canonical source at audit time | Project tree SHA | Reason |
| --- | --- | --- | --- |
| Predictive Survival 26.1.2 | `main` / `feat/predictive-survival-contingency-planner` | `b4e8e476ed7ed3393ce945ad573ac696fbb37306` | Exact tree equality after verified contingency upgrade merged on 2026-08-23 and coordination closed. |
| Pearl Catcher 26.1.2 | `main` / `fix/pearl-catcher-26-1-2-hardening` | `9bf19d6843f4b6118b3d5f978dd4ef7bf562f09d` | Exact tree equality; hardening is already present on `main`. |
| Fallen Knight 26.1.2 | `fix/fallen-knight-playtest-v2` (PR #17) | `0a6810cb24a49d51e21615f389d4d9733d685f6a` | Newer playtest fixes than `main`. Apparent `v3` is a divergent recovery branch containing Base64 recovery chunks and is 21 commits behind v2, so it is not canonical code. |
| Medusa 26.1.2 | `fix/medusa-dungeon-rebuild` (PR #16) | `fe509d34237a5006c5c4f10479db905372fe97f1` at audit | Active live repair/rebuild branch; latest staging task branch is an ancestor and the PR head is 100 commits ahead. Re-check head before snapshot. |
| Crystal Anchor Optimizer / CrystalBot | `feat/crystalbot-v3-world-class` (PR #24) | `2a19af02aed059341907adda96f2824468cadf4e` at audit | Latest intentional stacked lineage: V2 kickstart -> lethal-efficiency -> V3 Combat Core. Re-check head before snapshot. |
| Hypershot | `main` / `release/hypershot-0.2.0-beta.1` | `c87bfbed23c230b482281fd4845963c14fa38b5c` | Production branch differs only by `.branch-marker`; no newer project implementation files were found in that comparison. Use the clean release/main tree, not the marker-only tree. |
| Spear Client 26.1.2 | `feat/spear-client-26-1-2` | `e1ab81d52a80015078aa3f1cf1295143551c032f` | Only real implementation branch found. Task remains open and runtime verification is incomplete, so preserve it as latest development state without claiming it is release-complete. |
| SpeedBridge Assist | existing SpeedBridge PR/task artifact lineage | N/A | The implementation is transported as a verified baseline archive plus checksummed patch fragments reconstructed in CI; no honest full current `projects/...` source tree exists in this repository. Preserve as a legacy exception until a verified source materialization is performed separately. |

## Active PR handling

At audit time the following open PRs are structurally relevant:

- PR #16 Medusa: base `main`, head `fix/medusa-dungeon-rebuild`; active and updated 2026-08-24.
- PR #17 Fallen Knight: base `main`, head `fix/fallen-knight-playtest-v2`.
- PR #4/#5/#10 SpeedBridge: base `main`; historical design/CI work with no normal project tree.
- PR #19 Crystal V2 kickstart: base `design/crystal-optimizer-v2`.
- PR #23 Crystal lethal-efficiency: intentionally stacked on PR #19 head.
- PR #24 CrystalBot V3: intentionally stacked on PR #23 head.

Before merging the main cleanup, PR #4, #5, #10, #16, and #17 must move from `main` to the legacy pre-split base. Their heads must not be changed.

## `main` contents after split

Keep:

- `.agent-workspace.json`
- `agentctl.py`
- `src/agent_workspace/`
- `tests/`
- `schemas/`
- `agents/`
- `tasks/`
- shared documentation
- `.github/workflows/validate.yml`
- `.github/workflows/snapshot-backup.yml`

Remove from `main`:

- all ordinary `projects/<project>` implementation trees
- project-specific CI workflows for Crystal, Fallen Knight, Hypershot, Medusa, Pearl Catcher, Predictive Survival
- `speedbridge-assist-ci.yml` only if doing so cannot break the preserved legacy SpeedBridge PR lineage; otherwise leave it temporarily as an explicitly documented legacy exception

`projects/` may disappear entirely because Git does not track empty directories. Documentation must say this is expected.

## Project branch construction

For each normal project:

1. Start from the final cleaned `main` commit.
2. Add exactly one `projects/<project-id>` tree by its verified tree SHA.
3. Add the corresponding project workflow from the newest project lineage.
4. Update that workflow's `push.branches` to the new `project/<project-id>` branch instead of `main` or obsolete work branches. Preserve `pull_request` path filtering.
5. Do not copy unrelated task artifacts, `incoming/`, `recovery/`, or other project directories from the source branch.
6. Run/observe project CI. A project branch that cannot be verified must be labeled/documented as development-state rather than release-state.

Canonical branch names:

- `project/crystal-anchor-combat-optimizer-26-1-2`
- `project/fallen-knight-26-1-2`
- `project/hypershot`
- `project/medusa-26-1-2`
- `project/pearl-catcher-26-1-2`
- `project/predictive-survival-26-1-2`
- `project/spear-client-26-1-2`

SpeedBridge remains on its existing artifact-based lineage until a separate verified source-materialization task can produce a canonical full tree.

## Agent instructions after cleanup

`AGENTS.md`, `README.md`, `CONTRIBUTING.md`, and coordination docs must make these rules explicit:

- Workspace/protocol change -> branch from current `main`.
- New independent project -> create `project/<project-id>` from current clean `main`; project implementation first lands there.
- Existing project change -> branch from that project's `project/<project-id>`, not `main`.
- Intentional dependent work -> stack from the dependency branch and state that dependency in the PR.
- Coordination reads/writes still use `main` as source of truth even while implementation lives on a project branch.
- Never merge another project's implementation into a project branch just to obtain workspace updates. Bring shared workspace updates from `main` carefully without importing project trees.
- Canonical project selection uses tree/content/ancestry evidence, not branch-name recency.

## Duplicate and obsolete branch policy

Classify branches into four sets:

1. **Canonical:** `main`, `project/...`, and the pre-split backup/legacy base.
2. **Active:** open PR heads and intentional stacked dependencies.
3. **Recoverable history:** meaningful old release/design branches not proven redundant.
4. **Disposable:** probe/tmp branches or staging branches whose tips are proven ancestors of retained branches and are not PR heads.

Delete only set 4 when branch-ref deletion is supported. Otherwise keep a generated supersession manifest and do not use those refs for new work. Do not manufacture confidence by deleting branches whose uniqueness was not checked.

## Verification gates

The migration is complete only when:

1. `backup/pre-project-split-2026-08-24` resolves to the exact pre-cleanup main commit `5a64ea68509f08742995220cbd80319e19517d63`.
2. Every canonical project branch contains exactly one ordinary project implementation tree.
3. Project tree SHAs on canonical branches match the selected newest source trees (or a newer re-checked active head).
4. `main` contains no ordinary project implementation tree.
5. Main-based active PRs are protected from the split by the legacy base.
6. Workspace unit tests pass.
7. `python agentctl.py validate` returns `{ "errors": [], "ok": true }`.
8. Available project CI gates are green or any genuine project-specific failure is reported without masking it.
9. Final task records state exactly what was retained, migrated, superseded, and intentionally left as a legacy exception.
