# Worked multi-agent workflow

A coordinator splits a parser feature into `src/parser`, `tests/parser`, and `docs/parser`.

```bash
python agentctl.py register --id coordinator-91aa --provider human --model n-a
python agentctl.py task-create \
  --id parser-feature \
  --title "Add structured parser" \
  --created-by coordinator-91aa \
  --objective "Parse task manifests into validated Python objects" \
  --scope src/parser \
  --scope tests/parser \
  --scope docs/parser \
  --accept "Invalid manifests produce clear errors" \
  --accept "Parser behavior is covered by tests"
```

Three agents register unique instances, inspect the task, and claim distinct scopes. Each claim is made authoritative before implementation begins.

```bash
python agentctl.py claim --task parser-feature --scope src/parser --agent codex-parser-11af
python agentctl.py claim --task parser-feature --scope tests/parser --agent claude-tests-22be
python agentctl.py claim --task parser-feature --scope docs/parser --agent gemini-docs-33cd
```

The implementation agent finishes first and hands exact verification instructions to the test agent. It then releases `src/parser`. The test agent refreshes, reads the handoff, runs the tests, records results, and releases `tests/parser`.

The key is that agents do not share a mutable checklist. They coordinate through exclusive scope files and append-only records.
