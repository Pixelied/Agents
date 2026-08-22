from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FN = ROOT / "datapacks/medusa/data/medusa/function"


class MazeRecoveryContract(unittest.TestCase):
    def test_restart_recovery_uses_committed_current_state(self):
        cleanup = FN / "maze/recovery/cleanup_transient.mcfunction"
        rebuild = FN / "maze/recovery/rebuild_committed.mcfunction"
        recover = FN / "maze/recovery/recover_one.mcfunction"
        for path in [cleanup, rebuild, recover]:
            self.assertTrue(path.is_file(), f"missing maze recovery function: {path.name}")
        cleanup_text = cleanup.read_text()
        self.assertIn("md.maze.wall_display", cleanup_text)
        self.assertIn("md.maze.trap_helper", cleanup_text)
        self.assertIn("md_eid=$(eid)", cleanup_text)
        rebuild_text = rebuild.read_text()
        for current in ["md_mn", "md_me", "md_ms", "md_mw"]:
            self.assertIn(current, rebuild_text)
        recover_text = recover.read_text()
        self.assertIn("md_dungeon_clear", recover_text)
        self.assertIn("md_mphase 2", recover_text)
        self.assertIn("md_mphase 9", recover_text)

    def test_instance_recovery_and_reset_delegate_to_maze_cleanup(self):
        instance_recover = (FN / "instance/recover_one.mcfunction").read_text()
        reset_cleanup = (FN / "arena/reset/cleanup_scoped.mcfunction").read_text()
        self.assertIn("medusa:maze/recovery/recover_one", instance_recover)
        self.assertIn("md.maze.wall_display", reset_cleanup)
        self.assertIn("md.maze.trap_helper", reset_cleanup)
        self.assertIn("md_eid=$(eid)", reset_cleanup)
        self.assertNotIn("tag=md.maze.cell", reset_cleanup)


if __name__ == "__main__":
    unittest.main()
