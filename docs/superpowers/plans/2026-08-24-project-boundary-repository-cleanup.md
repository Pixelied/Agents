# Project-Boundary Repository Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `main` into a coordination-only branch while preserving the newest real implementation state of every project on clean, dedicated `project/...` branches without breaking active pull requests.

**Architecture:** Protect the old mixed-main topology behind an immutable backup/legacy ref, retarget open PRs that still depend on it, then merge a reviewable cleanup that removes project trees and project-specific CI from `main`. Build each canonical project branch from the cleaned `main` by Git-tree transplantation of exactly one audited project tree, so unrelated/stale project copies from historical branches can never be imported accidentally.

**Tech Stack:** Git/GitHub refs and trees, GitHub Actions, Python workspace validator, repository coordination protocol.

**Spec:** `docs/superpowers/specs/2026-08-24-project-boundary-repository-cleanup-design.md`

## Global Constraints

- Never force-update an active PR head or another agent's working branch.
- Canonical project selection is based on project-tree SHA, ancestry, PR intent, and verification state; branch naming is not evidence of freshness.
- `backup/pre-project-split-2026-08-24` must retain commit `5a64ea68509f08742995220cbd80319e19517d63`.
- Open PRs based on old `main` must be protected by a legacy base before `main` loses project trees.
- New project branches start from cleaned `main` and contain only one ordinary project implementation tree.
- Coordination source of truth remains `main`.
- Branches with unique or unverified history are not deleted.
- SpeedBridge remains a documented artifact-based legacy exception until a separate verified full-source materialization exists.

---

### Task 1: Freeze Migration Evidence and Safety Refs

**Files:**
- Existing: `tasks/clean-repository-project-boundaries/task.json`
- Existing: `tasks/clean-repository-project-boundaries/events/`
- Git refs: `backup/pre-project-split-2026-08-24`, `cleanup/project-boundary-migration`

**Interfaces:**
- Consumes: current `main` and audited branch/PR state.
- Produces: immutable rollback ref and isolated migration branch.

- [ ] **Step 1: Verify the rollback ref**

Confirm `backup/pre-project-split-2026-08-24` resolves to `5a64ea68509f08742995220cbd80319e19517d63`.

- [ ] **Step 2: Re-read moving active heads**

Record current head/tree for PR #16 Medusa, PR #17 Fallen Knight, and PR #24 CrystalBot immediately before canonical snapshot construction.

- [ ] **Step 3: Verify fixed canonical trees**

Confirm:

```text
Predictive Survival: b4e8e476ed7ed3393ce945ad573ac696fbb37306
Pearl Catcher:       9bf19d6843f4b6118b3d5f978dd4ef7bf562f09d
Hypershot:           c87bfbed23c230b482281fd4845963c14fa38b5c
Spear Client:        e1ab81d52a80015078aa3f1cf1295143551c032f
```

- [ ] **Step 4: Record evidence event**

Append a progress event with the final canonical-source matrix and explicit exceptions.

---

### Task 2: Protect Open PRs from the Main Split

**Files:**
- Pull request metadata only; do not change head branches.

**Interfaces:**
- Consumes: legacy backup ref with old mixed-main content.
- Produces: stable PR bases independent of the cleaned `main`.

- [ ] **Step 1: Snapshot PR metadata**

Capture base/head/changed-file counts for PR #4, #5, #10, #16, and #17.

- [ ] **Step 2: Retarget old-main PRs**

Set each PR's base branch to `backup/pre-project-split-2026-08-24` without changing its head.

- [ ] **Step 3: Re-read PR metadata**

Confirm every targeted PR is still open and points at the exact same head SHA as before.

- [ ] **Step 4: Confirm no project head changed**

If any head SHA moved during the retarget, stop using the stale audit snapshot and re-evaluate only that project before creating its canonical branch.

---

### Task 3: Document the New Branching Contract

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `CONTRIBUTING.md`
- Modify: `docs/protocols/coordination.md`
- Create: `docs/protocols/project-branches.md`

**Interfaces:**
- Consumes: approved design spec and canonical-source matrix.
- Produces: one unambiguous branching/ownership contract for humans and agents.

- [ ] **Step 1: Add project-base rules to `AGENTS.md`**

State explicitly:

```text
Workspace/protocol work -> branch from main.
New project -> create project/<project-id> from clean main.
Existing project work -> branch from project/<project-id>.
Dependent unmerged work -> stack intentionally and declare the dependency.
Coordination reads/writes -> main remains source of truth.
```

Also forbid copying/merging unrelated project trees to obtain workspace updates.

- [ ] **Step 2: Rewrite the README repository map**

Explain that `main` normally has no project implementations; project code is reached through canonical `project/...` branches.

- [ ] **Step 3: Tighten contributor flow**

Make `CONTRIBUTING.md` distinguish workspace branches from project branches and require the correct base.

- [ ] **Step 4: Extend coordination protocol**

Document that implementation branch ancestry and coordination source-of-truth are separate concepts: agents coordinate against `main` while editing their project branch.

- [ ] **Step 5: Create `project-branches.md`**

Include the canonical project list, legacy SpeedBridge exception, backup/legacy branch purpose, and the rule that canonical freshness is checked by content/ancestry rather than branch naming.

---

### Task 4: Remove Ordinary Projects and Project CI from Migration Main

**Files:**
- Delete from cleanup branch: `projects/crystal-anchor-combat-optimizer-26-1-2/**`
- Delete from cleanup branch: `projects/fallen-knight-26-1-2/**`
- Delete from cleanup branch: `projects/hypershot/**`
- Delete from cleanup branch: `projects/medusa-26-1-2/**`
- Delete from cleanup branch: `projects/pearl-catcher-26-1-2/**`
- Delete from cleanup branch: `projects/predictive-survival-26-1-2/**`
- Delete from cleanup branch: project-specific workflow files for those six projects.
- Preserve: `.github/workflows/validate.yml`
- Preserve: `.github/workflows/snapshot-backup.yml`
- Preserve temporarily if required by legacy PRs: `.github/workflows/speedbridge-assist-ci.yml`

**Interfaces:**
- Consumes: PR protection from Task 2.
- Produces: coordination-only candidate `main` tree.

- [ ] **Step 1: Delete each ordinary project tree only from `cleanup/project-boundary-migration`**

Do not touch the backup branch or active project heads.

- [ ] **Step 2: Delete matching project-specific workflows from the cleanup branch**

Keep shared validation/backup workflows.

- [ ] **Step 3: Inspect repository root and workflow directory**

Expected: no ordinary project implementation directory remains; shared workspace files remain intact.

- [ ] **Step 4: Review cleanup diff**

Confirm the diff contains only the intended removals plus documentation/spec/plan changes and cleanup coordination metadata inherited from `main`.

---

### Task 5: Validate and Merge the Main Cleanup

**Files:**
- Test: `tests/`
- Validate: workspace metadata via `agentctl.py`

**Interfaces:**
- Consumes: cleanup branch from Tasks 3-4.
- Produces: verified cleaned `main`.

- [ ] **Step 1: Open a cleanup PR to `main`**

The PR description must list the rollback ref, protected legacy PRs, canonical project tree SHAs, SpeedBridge exception, and verification commands.

- [ ] **Step 2: Run workspace CI**

Required commands:

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

Expected: all unit tests pass and validator returns no errors.

- [ ] **Step 3: Inspect PR file list**

Reject the migration if unexpected agent/task history or shared workspace code is removed.

- [ ] **Step 4: Merge only with green workspace validation**

Merge without rewriting the protected project PR heads.

- [ ] **Step 5: Re-read `main`**

Confirm ordinary project implementation trees are absent and shared workspace validation remains present.

---

### Task 6: Create Canonical Project Branches from Clean Main

**Files/refs:**
- Create: `project/crystal-anchor-combat-optimizer-26-1-2`
- Create: `project/fallen-knight-26-1-2`
- Create: `project/hypershot`
- Create: `project/medusa-26-1-2`
- Create: `project/pearl-catcher-26-1-2`
- Create: `project/predictive-survival-26-1-2`
- Create: `project/spear-client-26-1-2`

**Interfaces:**
- Consumes: cleaned `main`, verified project tree SHAs, project workflow blobs.
- Produces: one clean canonical branch per normal project.

- [ ] **Step 1: Re-check active source heads**

Use the latest tree if PR #16/#17/#24 moved since Task 1.

- [ ] **Step 2: Create one branch per project from cleaned `main`**

Do not branch from old feature/fix branches.

- [ ] **Step 3: Transplant exactly one project tree into each branch**

No other `projects/*` tree may be copied.

- [ ] **Step 4: Add the matching project workflow**

Use the newest lineage's workflow content and update `push.branches` to the new `project/...` name. Keep `pull_request` path filters.

- [ ] **Step 5: Inspect each branch's `projects/` directory**

Expected: exactly one ordinary project directory.

---

### Task 7: Verify Canonical Project Branches

**Files:**
- Project-specific CI workflows on each canonical branch.

**Interfaces:**
- Consumes: project branches from Task 6.
- Produces: verification matrix separating green, development-only, and legacy-exception states.

- [ ] **Step 1: Confirm tree identity**

Compare the canonical project directory tree SHA with the selected source tree SHA for every project.

- [ ] **Step 2: Observe project CI**

For every runnable project workflow, require a completed result. Do not mask a genuine project test failure to make the cleanup look green.

- [ ] **Step 3: Treat Spear honestly**

Its task is open and its latest event still lists real Java 25 Loom build/client/runtime verification as pending. Preserve latest source but label it development-state if CI/runtime proof is incomplete.

- [ ] **Step 4: Treat SpeedBridge honestly**

Do not invent a canonical source tree. Keep its current artifact/patch lineage and document the separate materialization follow-up.

---

### Task 8: Classify Duplicates and Obsolete Branches

**Files:**
- Update: `docs/protocols/project-branches.md`
- Append: cleanup task event with supersession inventory.

**Interfaces:**
- Consumes: complete branch list and retained canonical/active refs.
- Produces: auditable branch classification and safe cleanup boundary.

- [ ] **Step 1: Mark canonical and active refs**

Never delete `main`, `project/...`, backup/legacy, open PR heads, or intentional stacked bases.

- [ ] **Step 2: Prove redundancy before disposal**

A staging branch is disposable only when its tip is an ancestor of a retained branch or its unique changes are confirmed to be noncanonical probe/recovery transport.

- [ ] **Step 3: Handle tmp/probe branches**

Classify obvious probe refs separately from meaningful historical releases/designs.

- [ ] **Step 4: Delete only where supported and proven safe**

If the available GitHub connector cannot delete branch refs, do not simulate deletion. Record them as superseded and make the canonical-branch rules prevent reuse.

---

### Task 9: Final Workspace Verification and Coordination Closeout

**Files:**
- Update: `tasks/clean-repository-project-boundaries/task.json`
- Append: `tasks/clean-repository-project-boundaries/events/<completion>.json`
- Update: cleanup agent profile and lease files.

**Interfaces:**
- Consumes: final cleaned main + canonical project branches.
- Produces: closed, reproducible migration state.

- [ ] **Step 1: Run final workspace validation against final `main`**

Required:

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

- [ ] **Step 2: Verify rollback and canonical refs again**

Confirm backup SHA, project branch existence, one-project-only trees, and protected PR heads.

- [ ] **Step 3: Append completion evidence**

Record exact canonical project tree SHAs, CI results, retained legacy refs, and any branch-ref cleanup limitation.

- [ ] **Step 4: Mark the cleanup task complete**

Only after all acceptance criteria that are possible in the current connector are satisfied.

- [ ] **Step 5: Release every cleanup lease**

Set all cleanup lease records to released with timestamps.

- [ ] **Step 6: Take the one-shot cleanup agent offline**

Set `openai-repo-cleanup-24a8` to `offline` after no active cleanup lease remains.
