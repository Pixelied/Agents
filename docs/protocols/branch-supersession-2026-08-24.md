# Branch supersession registry — 2026-08-24

This registry is the authoritative cleanup classification for the branch set that existed after the project-boundary migration.

It answers one question for agents: **may I use this branch as the base for new work?**

Unless a branch is in the canonical or protected-live sections below, the answer is no.

The connected GitHub tooling used for this migration can create/update refs but does not expose branch/ref deletion. No alternate installed plugin provides that capability. Therefore this cleanup does not pretend old refs were physically deleted. Instead, canonical refs are made authoritative and old refs are classified here so future agents cannot accidentally revive them. Physical deletion can be performed later only with equivalent safety evidence.

## 1. Canonical refs — use these

### Workspace

- `main` — coordination/workspace source of truth. Ordinary project implementation trees do not belong here.

### Project bases

- `project/crystal-anchor-combat-optimizer-26-1-2`
- `project/fallen-knight-26-1-2`
- `project/hypershot`
- `project/medusa-26-1-2`
- `project/pearl-catcher-26-1-2`
- `project/predictive-survival-26-1-2`
- `project/spear-client-26-1-2`

Each project branch was synthesized from clean `main` plus exactly one audited newest project tree. Exact tree SHAs are recorded in `docs/protocols/project-branches.md`.

### Migration safety

- `backup/pre-project-split-2026-08-24` — immutable rollback snapshot at `5a64ea68509f08742995220cbd80319e19517d63`; never use as a normal work base.
- `legacy/mixed-main-2026-08-24` — compatibility base for pre-split open PRs; never use for new work.

## 2. Protected live PR lineages — do not rewrite or delete

These refs are still part of open PRs or an intentional stacked dependency chain. They are not canonical bases for new independent work, but they must remain intact until their PRs are deliberately resolved.

- `design/speedbridge-breezily-silent-aim` — PR #4 head.
- `ci/speedbridge-assist-1.1.0` — PR #5 head.
- `ci/speedbridge-26-2-hook-inspect` — PR #10 head.
- `fix/fallen-knight-playtest-v2` — PR #17 head; newest Fallen Knight tree was copied into the canonical project branch.
- `fix/medusa-dungeon-rebuild` — PR #16 head; newest Medusa tree was copied into the canonical project branch.
- `design/crystal-optimizer-v2` — base required by the live Crystal stacked chain.
- `fix/crystal-optimizer-v2-kickstart` — PR #19 head and PR #23 base.
- `feat/crystal-optimizer-v2-lethal-efficiency` — PR #23 head and PR #24 base.
- `feat/crystalbot-v3-world-class` — PR #24 head; newest Crystal tree was copied into the canonical project branch.

The project-boundary cleanup never force-updated these heads.

## 3. Historical/reference refs — preserve unless a later archival pass proves they are disposable

These branches may contain useful release, backup, design, or workspace history. They are not valid bases for new project implementation work.

### Backups

- `backup/main-2026-08-01`
- `backup/predictive-survival-hardening-6c12750`
- `backup/weekly-2026-08-02`
- `backup/weekly-2026-08-23`

### Design/release/workspace history

- `design/predictive-survival-26-1-2`
- `plan/predictive-survival-26-1-2`
- `release/hypershot-0.2.0-beta.1`
- `feat/multi-agent-workspace`
- `fix/post-first-run-hardening`

These refs are history only. New work uses `main` or the corresponding `project/...` branch.

## 4. Superseded project/CI/coordination refs — do not use for new work

The latest relevant project state has been captured elsewhere, or the branch served only as an intermediate migration/recovery/CI/coordination step. Keep only as historical evidence until ref-deletion tooling is available and a deletion pass confirms no remaining external dependency.

### Predictive Survival

- `ci/predictive-survival-26-1-2-hardening`
- `ci/predictive-survival-hardening-publish-v15`
- `ci/predictive-survival-hardening-recovery-check`
- `ci/predictive-survival-hardening-verify`
- `coord/predictive-survival-contingency-closeout`
- `coord/predictive-survival-review-7c4d-claim`
- `coord/release-predictive-survival-review-lease`
- `feat/predictive-survival-26-1-2-sol`
- `feat/predictive-survival-26-1-2`
- `feat/predictive-survival-contingency-planner`
- `fix/predictive-survival-26-1-2-hardening`
- `fix/predictive-survival-26-1-2-pristine`
- `fix/predictive-survival-review-hardening`

Canonical implementation: `project/predictive-survival-26-1-2`.

### Fallen Knight

- `feat/fallen-knight-26-1-2`
- `fix/fallen-knight-playtest`
- `fix/fallen-knight-playtest-v3`

`fix/fallen-knight-playtest-v3` is specifically **not** newer implementation code despite its name: it was a divergent recovery transport with Base64 chunks and was behind the real v2 lineage.

Canonical implementation: `project/fallen-knight-26-1-2`. PR #17's `fix/fallen-knight-playtest-v2` remains protected while open.

### Medusa

- `feat/medusa-26-1-2`
- `stage/medusa-full-forceload-fix`
- `stage/medusa-initial-validation-fix`
- `stage/medusa-parser-and-maze-diagnostics`
- `stage/medusa-shifting-task2`
- `stage/medusa-shifting-task3`
- `stage/medusa-shifting-task4`
- `stage/medusa-shifting-task5`
- `stage/medusa-shifting-task6`
- `stage/medusa-shifting-task7`
- `stage/medusa-shifting-task7-fix`
- `stage/medusa-shifting-task8`
- `stage/medusa-shifting-task9`
- `stage/medusa-shifting-task10`
- `stage/medusa-shifting-task10-fix`
- `work/medusa-build-staging`
- `work/medusa-maze-constructive-proposal`
- `work/medusa-shifting-maze-inline`

Canonical implementation: `project/medusa-26-1-2`. PR #16's `fix/medusa-dungeon-rebuild` remains protected while open.

### HyperShot

- `coord/hypershot-26-2-7c4e`
- `feat/hypershot-26-2-production`
- `feature/hypershot-26-2`

The production branch's project-tree difference was only `.branch-marker`; the implementation matched the selected clean release tree.

Canonical implementation: `project/hypershot`.

### Pearl Catcher

- `coord/pearl-catcher-hardening-claim`
- `fix/pearl-catcher-26-1-2-hardening`

Canonical implementation: `project/pearl-catcher-26-1-2`.

### Spear Client

- `feat/spear-client-26-1-2`

Its latest tree was copied exactly to `project/spear-client-26-1-2`. The old project lease was already expired at migration time. The canonical branch preserves development state; it is not a release-completeness claim.

### Crystal historical implementation/package refs

- `package/crystal-anchor-combat-optimizer-26-1-2`
- `work/crystal-anchor-combat-optimizer-26-1-2`
- `work/crystal-optimizer-v2`

The open stacked V2/V3 PR chain remains protected above. New independent Crystal work starts from `project/crystal-anchor-combat-optimizer-26-1-2`.

### SpeedBridge intermediate CI

- `ci/speedbridge-breezily-milestone-1-5`

The actual open SpeedBridge PR heads remain protected. SpeedBridge itself remains the documented artifact/patch legacy exception until a verified full source tree is materialized.

### Migration branch

- `cleanup/project-boundary-migration` — merged as PR #33; never use as a new work base.

## 5. Ephemeral/probe refs — deletion candidates

These names explicitly identify temporary assembly/probe work. None is a canonical branch or an open PR head in the 2026-08-24 audit. They must never be used as bases for new work and are the first candidates for physical deletion when ref deletion becomes available.

- `tmp/cleanup-probe`
- `tmp/delete-me`
- `tmp/ignore-this`
- `tmp/last-probe`
- `tmp/predictive-survival-baked-latest`
- `tmp/predictive-survival-pristine-assemble`
- `tmp/predictive-survival-pristine-assemble-2`
- `tmp/ps-final-final`
- `tmp/ps-pristine`
- `tmp/ps-pristine-tree`
- `tmp/what`
- `tmp-noop-should-not-create`

## 6. Rule for future cleanup

Branch deletion is a destructive history operation. When deletion tooling is available:

1. Refresh the open PR list and active leases.
2. Never delete a canonical, rollback, legacy-compatibility, or active PR/dependency ref.
3. Delete ephemeral/probe refs first.
4. For superseded project refs, verify there is no external PR/tag/release/workflow dependency and that the canonical tree still matches or supersedes the desired implementation.
5. Preserve meaningful release/design/backup history unless there is a separate archival decision.
6. Record every deleted ref in task history.

The existence of an old branch does not make it valid. `AGENTS.md` and `docs/protocols/project-branches.md` define the only normal bases for new work.
