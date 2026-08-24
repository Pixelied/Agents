# Project branch protocol

`main` is the coordination source of truth. It is not the canonical implementation branch for ordinary projects.

## Canonical branch rule

Every normal project has one long-lived branch named:

```text
project/<project-id>
```

Use the following base rules:

```text
Workspace/protocol/tooling work -> main
New independent project         -> project/<project-id> created from clean main
Existing project work           -> project/<project-id>
Dependent unmerged work         -> dependency branch, intentionally stacked
```

A normal project branch inherits the shared coordination workspace from clean `main` and adds exactly one ordinary project implementation tree under `projects/`, plus that project's CI workflow. It should not contain unrelated project trees.

Coordination writes still synchronize against `main` even when implementation work lives elsewhere.

## Canonical projects established by the 2026-08-24 cleanup

| Project | Canonical branch | Selected source/tree at migration audit |
| --- | --- | --- |
| Crystal Anchor Optimizer / CrystalBot | `project/crystal-anchor-combat-optimizer-26-1-2` | latest `feat/crystalbot-v3-world-class` project tree; active head is re-checked at snapshot time |
| Fallen Knight 26.1.2 | `project/fallen-knight-26-1-2` | `fix/fallen-knight-playtest-v2`, tree `0a6810cb24a49d51e21615f389d4d9733d685f6a` |
| Hypershot | `project/hypershot` | release/main tree `c87bfbed23c230b482281fd4845963c14fa38b5c` |
| Medusa 26.1.2 | `project/medusa-26-1-2` | latest `fix/medusa-dungeon-rebuild` project tree; active head is re-checked at snapshot time |
| Pearl Catcher 26.1.2 | `project/pearl-catcher-26-1-2` | tree `9bf19d6843f4b6118b3d5f978dd4ef7bf562f09d` |
| Predictive Survival 26.1.2 | `project/predictive-survival-26-1-2` | verified contingency tree `b4e8e476ed7ed3393ce945ad573ac696fbb37306` |
| Spear Client 26.1.2 | `project/spear-client-26-1-2` | development tree `e1ab81d52a80015078aa3f1cf1295143551c032f` |

The cleanup's detailed source-selection evidence is in `docs/superpowers/specs/2026-08-24-project-boundary-repository-cleanup-design.md`.

## SpeedBridge legacy exception

SpeedBridge Assist does not currently have an honest full canonical `projects/...` source tree in this repository. Its open lineage reconstructs a verified baseline archive and applies checksummed patch artifacts in CI. Do not manufacture a `project/speedbridge-*` tree from incomplete patch files.

Until a separate source-materialization task verifies and commits a complete tree, the existing SpeedBridge design/CI PR lineage is a documented legacy exception.

## Legacy mixed-main compatibility

The 2026-08-24 migration preserves two refs:

- `backup/pre-project-split-2026-08-24` — immutable rollback snapshot of the old mixed `main` at `5a64ea68509f08742995220cbd80319e19517d63`.
- `legacy/mixed-main-2026-08-24` — compatibility base for open PRs created before the split.

Do not start new implementation work from the legacy branch. It exists only so old PRs can finish without their diffs being corrupted by the main-branch cleanup.

## Freshness and duplicate rules

Never infer "newest" from a branch name such as `v2`, `v3`, `final`, `latest`, `fix`, or a timestamp. Determine canonical state using:

1. actual project tree/content identity;
2. ancestry and whether one branch contains another's work;
3. PR/task intent;
4. verification/release state;
5. active development state when the user explicitly wants the newest development copy.

Recovery, staging, `tmp/`, `coord/`, and CI branches may contain useful evidence but are not canonical merely because they are newer Git commits.

Examples found during the migration:

- `fix/fallen-knight-playtest-v3` looked newer by name but was a divergent recovery branch containing Base64 chunks and was 21 commits behind the real v2 implementation lineage.
- `feat/hypershot-26-2-production` had a different project-tree SHA only because of `.branch-marker`; the actual implementation matched the clean release/main copy.

## Project workflow ownership

Project-specific CI belongs on the corresponding canonical project branch. Its `push` filter should name the canonical `project/...` branch; `pull_request` path filters should continue to protect project changes.

Shared workspace validation and backup automation belong on `main`.

## Updating shared workspace files on a project branch

When a project branch needs newer shared tooling from `main`, integrate only the shared workspace changes required by that project. Do not import `projects/*` from another project or an old mixed branch.

If the update is risky or crosses leased scopes, coordinate it as a separate task.

## Active and stacked work

Do not rewrite an active PR head to make history prettier. Intentionally stacked PRs are valid when the dependency is real and explicitly represented by the PR base. Once the stack is resolved, future independent work should return to the canonical `project/<project-id>` base.
