# Physical branch prune manifest — 2026-08-24

Status: **completed**

The physical branch prune completed successfully on 2026-08-24 through GitHub Actions run `32728525649`.

## Result

- Branches before pruning: **87**
- Explicit deletion candidates: **67**
- Candidate heads archived: **67**
- Branches physically deleted: **67**
- Missing or skipped candidates: **0**
- Branches after pruning: **20**
- Archive branch: `archive/pre-branch-prune-2026-08-24`
- Archive tip: `256ff367e2f5f2382dee4d6fa23c322005d5e4b4`
- Archive lock used before deletion: `ffde333abbb6c0bbb4d91d1181deed103fe0f85b`
- Main commit used by the deletion transaction: `49ddae79addad46d35eda7d10778f2845192017d`

The archive branch contains `docs/protocols/branch-prune-archive-2026-08-24.json`, which records every deleted branch name, its exact pre-deletion head SHA and committer date, the open-PR protection snapshot, the canonical protection set, and the audit cutoff. The archive commit ancestry also retains every deleted branch head, so deletion of the branch names did not discard the audited commit history.

## Surviving branches

These are the complete surviving branch set immediately after the prune.

### Workspace and recovery

```text
main
archive/pre-branch-prune-2026-08-24
backup/pre-project-split-2026-08-24
legacy/mixed-main-2026-08-24
```

`archive/pre-branch-prune-2026-08-24` and `backup/pre-project-split-2026-08-24` are recovery/history refs, not normal development bases. `legacy/mixed-main-2026-08-24` exists only while pre-split PRs still depend on it.

### Canonical project branches

```text
project/crystal-anchor-combat-optimizer-26-1-2
project/fallen-knight-26-1-2
project/hypershot
project/medusa-26-1-2
project/pearl-catcher-26-1-2
project/predictive-survival-26-1-2
project/spear-client-26-1-2
```

### Branches retained only because current open PRs or stacked dependencies require them

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

These nine are not canonical bases for new independent work. They remain only until their current PR/dependency chains are deliberately resolved.

## Protected PR snapshot

The prune preserved the heads/bases needed by these pre-existing open PRs:

```text
PR #4  design/speedbridge-breezily-silent-aim       -> legacy/mixed-main-2026-08-24
PR #5  ci/speedbridge-breezily-milestone-1-5       -> legacy/mixed-main-2026-08-24
PR #10 ci/speedbridge-26-2-hook-inspect             -> legacy/mixed-main-2026-08-24
PR #16 fix/medusa-dungeon-rebuild                   -> legacy/mixed-main-2026-08-24
PR #17 fix/fallen-knight-playtest-v2                -> legacy/mixed-main-2026-08-24
PR #19 fix/crystal-optimizer-v2-kickstart           -> design/crystal-optimizer-v2
PR #23 feat/crystal-optimizer-v2-lethal-efficiency  -> fix/crystal-optimizer-v2-kickstart
PR #24 feat/crystalbot-v3-world-class               -> feat/crystal-optimizer-v2-lethal-efficiency
```

## Safety evidence

Before any deletion, the executor required all of the following:

1. the exact pre-created archive lock SHA;
2. all canonical branches to exist;
3. no candidate to be an open PR head/base;
4. no unexpired active lease owned by another agent;
5. no ordinary candidate to have moved after the audit cutoff;
6. successful construction and verification of the recovery archive;
7. a second open-PR check immediately before deletion;
8. each individual branch SHA to still match its archived SHA immediately before its DELETE request;
9. a 404 readback after every deletion.

The workflow reported `67` archived candidate heads, `67` successful deletions, and an empty missing/skipped list.

## Recovery

Do not recreate an old branch just because its former name is remembered. If historical recovery is genuinely needed, inspect the exact branch-to-SHA mapping in the archive JSON first and restore only the specific audited ref required for that purpose.
