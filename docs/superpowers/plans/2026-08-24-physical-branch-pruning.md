# Physical Branch Pruning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Physically reduce the repository branch list to canonical workspace/project refs, minimum rollback/legacy safety refs, and branches required by current open PR/dependency chains, without losing recoverability for any deleted branch history.

**Architecture:** Use one guarded, one-shot GitHub Actions workflow committed to `main`. The workflow operates on an explicit audited candidate list, dynamically protects all current open PR head/base refs, refuses to delete any ordinary candidate that moved after the audit cutoff, refuses to run while another agent owns any unexpired active lease, builds a single archive branch whose commit ancestry retains every deleted branch head, verifies the archive ref, and only then deletes candidates through the GitHub refs API. A post-run verification pass confirms the exact surviving branch set and normal workspace validation before the one-shot workflow and its temporary contract test are removed.

**Tech Stack:** GitHub REST API, GitHub Actions, Python 3.11 standard library, repository coordination protocol v1.0.

**Spec:** `docs/protocols/branch-supersession-2026-08-24.md`

## Global Constraints

- Never delete `main`.
- Never delete any canonical `project/<project-id>` branch.
- Never delete `backup/pre-project-split-2026-08-24` or `legacy/mixed-main-2026-08-24` during this pass.
- Never delete a branch that is the head or base of a currently open pull request at execution time.
- Do not infer deletion from prefixes alone; only branches in the explicit 2026-08-24 audited candidate list may be removed.
- Abort before the first deletion if an ordinary candidate branch moved after audit cutoff `2026-08-24T12:18:00Z`.
- `cleanup/physical-branch-prune-2026-08-24` is the single post-cutoff exception because it is owned by this pruning task itself; it may be deleted only after its PR has merged/closed and therefore no longer protects the branch dynamically.
- Abort before the first deletion if any other agent owns an unexpired active workspace lease.
- Before deletion, keep every candidate head reachable from `archive/pre-branch-prune-2026-08-24` using chained multi-parent archive commits.
- Preserve project implementation contents; this task changes refs and cleanup documentation only.
- Run `python -m unittest discover -s tests -v` and `python agentctl.py validate` after pruning and again after cleanup coordination closeout.

---

### Task 1: Freeze the live protection set and explicit candidate list

**Files:**
- Modify: `docs/protocols/branch-supersession-2026-08-24.md`
- Create: `docs/protocols/branch-prune-manifest-2026-08-24.md`

**Interfaces:**
- Consumes: current GitHub branch list and open PR metadata.
- Produces: exact protected branch set, exact 67-name deletion candidate set, audit cutoff, and archive-ref name consumed by Task 2.

- [ ] **Step 1: Re-read all open PR metadata immediately before workflow creation**

Collect every same-repository open PR `head` and `base` branch.

Expected protected live PR/dependency refs at the audit point:

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

- [ ] **Step 2: Record the non-PR protected refs**

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

- [ ] **Step 3: Record the explicit deletion candidates**

The workflow may delete only these 67 names:

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

- [ ] **Step 4: Commit the audit manifest before destructive execution**

Expected: manifest exists on the cleanup PR and is independently reviewable before the prune workflow runs.

### Task 2: Add and red-green test a one-shot fail-closed pruning workflow

**Files:**
- Create: `.github/workflows/prune-superseded-branches.yml`
- Create temporarily: `tests/test_branch_prune_contract.py`

**Interfaces:**
- Consumes: exact candidate/protected sets from Task 1 and GitHub REST API state at execution time.
- Produces: archive ref `archive/pre-branch-prune-2026-08-24` and physical deletion of only verified candidates.

- [ ] **Step 1: Add the contract test first and verify RED**

The test requires the one-shot workflow, explicit candidates, dynamic protection markers, archive construction, delete verification, and compilable embedded Python. Run the ordinary PR validation workflow and require failure specifically because the prune workflow is absent.

- [ ] **Step 2: Configure the workflow as a guarded one-shot push cleanup**

Use:

```yaml
on:
  push:
    branches: [main]
permissions:
  contents: write
  pull-requests: read
```

Guard the job with:

```text
startsWith(github.event.head_commit.message, 'chore: prune superseded branches')
```

- [ ] **Step 3: Implement REST and lease preflight in Python standard library**

The script must:

```text
GET /repos/{repo}/pulls?state=open&per_page=100
GET /repos/{repo}/branches?per_page=100 (paginate)
GET /repos/{repo}/commits/{sha} for each existing candidate
scan tasks/*/leases/*.json for unexpired active leases
```

Before any DELETE request, fail if:

```text
candidate in open PR head/base refs
candidate is one of the protected canonical/safety refs
ordinary candidate latest commit committer date > 2026-08-24T12:18:00Z
an unexpired active lease belongs to another agent
any expected canonical branch is missing
archive ref already exists at an unexpected commit
```

New branches not in the explicit candidate list are ignored, never deleted. The owned cleanup branch is the only cutoff exception.

- [ ] **Step 4: Build a reversible archive ref before deletion**

Create a JSON manifest blob containing each existing candidate branch and exact head SHA. Create an archive tree from current `main` plus `docs/protocols/branch-prune-archive-2026-08-24.json`.

Create chained archive commits in chunks of at most 20 unique candidate parent SHAs:

```text
archive-commit-1 parents = [current-main, candidate-1..candidate-20]
archive-commit-2 parents = [archive-commit-1, candidate-21..candidate-40]
archive-commit-3 parents = [archive-commit-2, candidate-41..candidate-60]
archive-commit-4 parents = [archive-commit-3, remaining candidates]
```

Every archive commit uses the same archive tree. Create `refs/heads/archive/pre-branch-prune-2026-08-24` at the final archive commit, then GET the ref and verify the SHA before proceeding.

- [ ] **Step 5: Delete candidates only after archive verification**

For each candidate still present and preflight-approved:

```text
DELETE /repos/{repo}/git/refs/heads/{url-encoded-branch}
```

After every deletion, GET the ref and require HTTP 404. If any deletion fails, stop immediately and leave the archive ref intact.

- [ ] **Step 6: Verify GREEN on the exact PR head**

Expected: temporary safety tests plus normal workspace tests all pass, embedded Python compiles, and `python agentctl.py validate` returns `errors: []`, `ok: true`.

### Task 3: Execute pruning and verify the surviving topology

**Files:**
- Modify: `docs/protocols/branch-supersession-2026-08-24.md`
- Modify: `docs/protocols/branch-prune-manifest-2026-08-24.md`

**Interfaces:**
- Consumes: completed Task 2 workflow run.
- Produces: authoritative post-prune branch registry.

- [ ] **Step 1: Merge the verified PR using the exact trigger title**

Merge PR #35 with merge commit title beginning exactly:

```text
chore: prune superseded branches
```

Use an expected-head SHA precondition so a concurrent PR-head move blocks the merge.

- [ ] **Step 2: Read the complete pruning workflow job log**

Expected: preflight passes, archive ref is created and verified, and only explicit candidates are deleted. If a current PR or live external lease blocks pruning, do not bypass it; leave refs untouched and investigate.

- [ ] **Step 3: Re-query the complete branch list**

Expected surviving refs are:

```text
main
archive/pre-branch-prune-2026-08-24
backup/pre-project-split-2026-08-24
legacy/mixed-main-2026-08-24
project/crystal-anchor-combat-optimizer-26-1-2
project/fallen-knight-26-1-2
project/hypershot
project/medusa-26-1-2
project/pearl-catcher-26-1-2
project/predictive-survival-26-1-2
project/spear-client-26-1-2
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

If a genuinely new branch appeared after the audit, it may also remain; record it and do not delete it automatically.

- [ ] **Step 4: Re-read all open PRs and archive metadata**

Expected: every pre-existing open PR retains its original head/base branch and remains open; PR #35 is merged/closed. Fetch the archive branch and its embedded branch→SHA manifest.

### Task 4: Remove one-shot machinery and close coordination state

**Files:**
- Delete: `tests/test_branch_prune_contract.py`
- Delete: `.github/workflows/prune-superseded-branches.yml`
- Modify: `docs/protocols/branch-supersession-2026-08-24.md`
- Modify: `docs/protocols/branch-prune-manifest-2026-08-24.md`
- Modify: `tasks/prune-superseded-branches-2026-08-24/task.json`
- Modify: `tasks/prune-superseded-branches-2026-08-24/leases/*.json`
- Modify: `agents/openai-branch-prune-8e51/profile.json`

**Interfaces:**
- Consumes: verified post-prune topology.
- Produces: clean shared workspace with no reusable destructive workflow and no active cleanup leases.

- [ ] **Step 1: Remove the temporary test first, then remove the one-shot workflow from `main`**

Deleting the test first prevents a transient shared-CI failure after the destructive workflow is removed. Neither cleanup commit uses the prune trigger message.

- [ ] **Step 2: Record exact deletion and archive metadata**

Update the branch supersession registry and manifest with the archive SHA, actual deleted list/count, any skipped candidate, and final surviving branch set.

- [ ] **Step 3: Run the shared workspace verification gate**

Run:

```text
python -m unittest discover -s tests -v
python agentctl.py validate
```

Expected: all shared tests pass and validator returns `errors: []`, `ok: true`.

- [ ] **Step 4: Mark task completed, release all five scopes, and set cleanup agent offline**

Expected: no active lease owned by `openai-branch-prune-8e51` remains.

- [ ] **Step 5: Run the same verification gate again after closeout**

Expected: all tests pass and validator returns `errors: []`, `ok: true` with the agent offline and leases released.
