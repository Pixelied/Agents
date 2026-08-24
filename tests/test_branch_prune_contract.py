from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "prune-superseded-branches.yml"


class BranchPruneWorkflowContractTests(unittest.TestCase):
    def workflow_text(self) -> str:
        self.assertTrue(WORKFLOW.is_file(), "one-shot branch prune workflow must exist during the prune PR")
        return WORKFLOW.read_text(encoding="utf-8")

    def test_workflow_is_one_shot_and_has_only_required_permissions(self):
        text = self.workflow_text()
        self.assertIn("branches: [main]", text)
        self.assertIn("contents: write", text)
        self.assertIn("pull-requests: read", text)
        self.assertIn("chore: prune superseded branches", text)

    def test_workflow_has_fail_closed_protection_gates(self):
        text = self.workflow_text()
        for required in (
            "2026-08-24T12:18:00Z",
            "archive/pre-branch-prune-2026-08-24",
            "open_pr_refs",
            "candidate moved after audit cutoff",
            "canonical branch missing",
            "archive verification failed",
        ):
            self.assertIn(required, text)

    def test_workflow_uses_explicit_candidates_not_prefix_sweeps(self):
        text = self.workflow_text()
        self.assertIn("CANDIDATES = [", text)
        self.assertIn('"tmp/delete-me"', text)
        self.assertIn('"cleanup/physical-branch-prune-2026-08-24"', text)
        self.assertNotIn("startswith(\"tmp/\")", text)
        self.assertNotIn("startswith(\"stage/\")", text)

    def test_workflow_archives_every_candidate_head_before_delete(self):
        text = self.workflow_text()
        for required in (
            "branch-prune-archive-2026-08-24.json",
            "archive_parents",
            "chunk_size = 20",
            "git/commits",
            "git/refs/heads/archive/pre-branch-prune-2026-08-24",
            "DELETE",
            "expected 404 after deleting",
        ):
            self.assertIn(required, text)


if __name__ == "__main__":
    unittest.main()
