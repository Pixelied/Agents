from __future__ import annotations

from pathlib import Path

from .coordination import CoordinationMixin
from .registry import RegistryMixin
from .storage import WorkspaceError
from .validation import ValidationMixin


class Workspace(RegistryMixin, CoordinationMixin, ValidationMixin):
    """High-level API for a Git-native multi-agent workspace."""

    def __init__(self, root: str | Path):
        self.root = Path(root).resolve()


__all__ = ["Workspace", "WorkspaceError"]
