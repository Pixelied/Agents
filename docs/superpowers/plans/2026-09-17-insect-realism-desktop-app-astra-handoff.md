# Insect Realism Desktop App — Astra One-Shot Handoff

This file is the execution entrypoint when the user manually supplies the design spec and implementation plan to Astra.

## Priority override

This handoff **supersedes any repository- or branch-specific execution instructions** in:

- `2026-09-17-insect-realism-desktop-app-design.md`, especially its repository/execution-base discussion;
- `2026-09-17-insect-realism-desktop-app.md`, especially Task 1 and any wording that assumes a particular branch, `main`, `project/...`, `Pixelied/Agents`, or `agentctl.py`.

The product architecture, implementation tasks, tests, performance targets, safety requirements, packaging requirements, and Definition of Done in those files remain authoritative. Only the repository/branch-selection mechanics are overridden here.

## Working-repository and branch rule

The user will provide the spec, plan, and relevant research/Mega Pack files manually. Work in the repository and branch that are already available in your environment.

You may:

- remain on the current branch if it is clean, appropriate, and safe for this project;
- create a fresh feature branch from the current useful state if that is cleaner;
- switch to another existing branch if repository evidence shows it is a better implementation base.

Choose the branch yourself. **Do not ask the user to choose a branch and do not force work onto `docs/insect-realism-desktop-app-design-a317` or any other branch named in the supplied files.**

Before editing, inspect the current repository's own instructions (`AGENTS.md`, `CLAUDE.md`, README, contribution docs, workspace rules, or equivalent) and obey them. If this repository has its own coordination/lease system, use it. If it does not, do not invent or import the `Pixelied/Agents` coordination protocol merely because the supplied plan mentions it.

Do not perform destructive branch cleanup, rewrite unrelated history, or overwrite unrelated work. If the current branch contains unrelated unfinished changes that make it unsafe, create a new branch from the most appropriate existing state and continue there.

## Input rule

Treat the manually supplied files as the authoritative task inputs. Do not waste the one-shot request searching old branches merely to rediscover copies of files the user already supplied.

Inspect the current repository for useful existing code, research outputs, assets, and project conventions, but do not require a historical Mega Pack branch to exist. If a supplied research/profile artifact has an equivalent newer verified version already present in the working repository, use the newer verified version and record that decision.

If the plan uses `$APP_ROOT`, resolve it to the most sensible project root in the repository you are actually working in. If the exact planned directory structure does not yet exist, create it in the location that best matches that repository's conventions while preserving the architectural boundaries from the plan.

## One-shot execution instruction

Read the supplied design spec and implementation plan in full, then execute the **entire** app build as one continuous assignment.

Start by:

1. inspecting the current repository and branch;
2. reading its local instructions and current status;
3. choosing the safest useful branch yourself;
4. locating the manually supplied Mega Pack/research inputs and any newer verified local equivalents;
5. resolving the application root and writing the execution log required by the implementation plan.

Then continue through the complete implementation plan without stopping between milestones: Rust workspace, profile compiler, settings and presets, physical display/calibration model, deterministic simulation, spatial indexing and trails, ant behavior/gait/antennae, procedural `wgpu` renderer, validation harness, macOS overlay, Windows overlay, menu/tray/settings UI, multi-monitor behavior, compatibility mode, recovery, secondary-creature evidence gate, performance optimization, packaging, CI, documentation, full tests, benchmarks, and final release verification.

Internal tasks and commits are checkpoints only. **Do not return to the user after each task and do not ask whether to continue.** Investigate and fix ordinary build failures, test failures, integration bugs, architecture mismatches, and performance misses autonomously. Continue until the Definition of Done is met or a genuine external blocker remains.

A genuine blocker is something that cannot be solved from the available repository/environment, such as unavailable signing credentials, missing required hardware for a hardware-only verification step, or repository permissions that prevent the needed operation. Complete every unaffected part before reporting such a blocker.

Do not claim completion because source files merely exist. Before the final report, inspect release-mode test results, benchmark results, visual-validation outputs, packaging results, platform smoke-test evidence, and the repository for unfinished required implementation.

## Exact prompt to use with Astra

> I am manually giving you the insect-realism desktop-app design spec, implementation plan, this Astra handoff file, and the relevant research/Mega Pack files. Read all of them in full. Execute the ENTIRE implementation plan as one continuous assignment. Work in the repository you currently have and choose the branch yourself: stay on the current branch if it is clean and appropriate, or create/switch to a better branch if needed. Do not force work onto any branch named in the supplied files, and do not ask me which branch to use. Follow the current repository's own instructions and preserve unrelated work. Treat this handoff as overriding the repo/branch-specific language in the older spec/plan while keeping all product, architecture, testing, performance, packaging, and Definition-of-Done requirements intact. Use the manually supplied research files directly unless the current repo contains a newer verified equivalent. Implement all tasks end-to-end, test-first where practical, fix ordinary failures autonomously, benchmark and optimize the 1,000+ creature target, build macOS and Windows artifacts as far as the environment permits, and do not stop between milestones to ask me whether to continue. Only stop early for a genuine external blocker after completing everything else you can. Do not claim completion until you have inspected final release-mode tests, benchmarks, visual validation, packaging, and platform-validation results.
