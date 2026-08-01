from __future__ import annotations

from pathlib import Path
from typing import Any, Iterable

from .storage import PROTOCOL_VERSION, WorkspaceError, ensure_scope, iso, read_json, write_json


class RegistryMixin:
    root: Path

    @property
    def agents_dir(self) -> Path:
        return self.root / "agents"

    @property
    def tasks_dir(self) -> Path:
        return self.root / "tasks"

    def initialize(self) -> None:
        self.agents_dir.mkdir(parents=True, exist_ok=True)
        self.tasks_dir.mkdir(parents=True, exist_ok=True)

    def agent_path(self, agent_id: str) -> Path:
        from .storage import ensure_identifier

        return self.agents_dir / ensure_identifier(agent_id, "agent id")

    def task_path(self, task_id: str) -> Path:
        from .storage import ensure_identifier

        return self.tasks_dir / ensure_identifier(task_id, "task id")

    def require_agent(self, agent_id: str) -> dict[str, Any]:
        return read_json(self.agent_path(agent_id) / "profile.json")

    def require_task(self, task_id: str) -> dict[str, Any]:
        return read_json(self.task_path(task_id) / "task.json")

    def register_agent(
        self,
        agent_id: str,
        provider: str,
        model: str,
        capabilities: Iterable[str],
        description: str = "",
    ) -> dict[str, Any]:
        self.initialize()
        path = self.agent_path(agent_id)
        profile_path = path / "profile.json"
        if not provider.strip() or not model.strip():
            raise WorkspaceError("Provider and model are required")
        try:
            path.mkdir(parents=True, exist_ok=False)
        except FileExistsError as exc:
            raise WorkspaceError(f"Agent {agent_id!r} already exists") from exc
        profile = {
            "schema_version": 1,
            "protocol_version": PROTOCOL_VERSION,
            "agent_id": agent_id,
            "provider": provider.strip(),
            "model": model.strip(),
            "description": description.strip(),
            "capabilities": sorted({item.strip() for item in capabilities if item.strip()}),
            "registered_at": iso(),
            "state": "available",
        }
        write_json(profile_path, profile)
        (path / "inbox").mkdir()
        (path / "notes").mkdir()
        (path / "README.md").write_text(
            f"# {agent_id}\n\nRuntime workspace for `{agent_id}`.\n\n"
            "- `profile.json` is the machine-readable identity.\n"
            "- `inbox/` contains direct coordination messages.\n"
            "- `notes/` contains agent-owned scratch notes only.\n",
            encoding="utf-8",
        )
        return profile

    def create_task(
        self,
        task_id: str,
        title: str,
        created_by: str,
        objective: str,
        scopes: Iterable[str],
        acceptance_criteria: Iterable[str] = (),
        priority: str = "normal",
    ) -> dict[str, Any]:
        self.initialize()
        self.require_agent(created_by)
        task_dir = self.task_path(task_id)
        task_file = task_dir / "task.json"
        scope_list = sorted({ensure_scope(scope.strip()) for scope in scopes if scope.strip()})
        if not title.strip() or not objective.strip():
            raise WorkspaceError("Task title and objective are required")
        if not scope_list:
            raise WorkspaceError("At least one task scope is required")
        for index, left in enumerate(scope_list):
            for right in scope_list[index + 1:]:
                if left.startswith(right + "/") or right.startswith(left + "/"):
                    raise WorkspaceError(f"Task scopes overlap: {left!r} and {right!r}")
        try:
            task_dir.mkdir(parents=True, exist_ok=False)
        except FileExistsError as exc:
            raise WorkspaceError(f"Task {task_id!r} already exists") from exc
        task = {
            "schema_version": 1,
            "protocol_version": PROTOCOL_VERSION,
            "task_id": task_id,
            "title": title.strip(),
            "objective": objective.strip(),
            "created_by": created_by,
            "created_at": iso(),
            "priority": priority,
            "status": "open",
            "scopes": scope_list,
            "acceptance_criteria": [item.strip() for item in acceptance_criteria if item.strip()],
        }
        for name in ("events", "leases", "handoffs", "artifacts"):
            (task_dir / name).mkdir(parents=True, exist_ok=True)
        write_json(task_file, task)
        (task_dir / "README.md").write_text(
            f"# {title.strip()}\n\n{objective.strip()}\n\n"
            "Machine-readable task metadata lives in `task.json`. Current state is derived "
            "from append-only events and active leases.\n",
            encoding="utf-8",
        )
        self.add_event(task_id, created_by, "task_created", f"Created task: {title.strip()}")
        return task

    def list_agents(self) -> list[dict[str, Any]]:
        agents: list[dict[str, Any]] = []
        if not self.agents_dir.is_dir():
            return agents
        for directory in sorted(self.agents_dir.iterdir()):
            profile = directory / "profile.json"
            if directory.is_dir() and profile.is_file():
                agents.append(read_json(profile))
        return agents

    def list_tasks(self) -> list[dict[str, Any]]:
        tasks: list[dict[str, Any]] = []
        if not self.tasks_dir.is_dir():
            return tasks
        for directory in sorted(self.tasks_dir.iterdir()):
            task_file = directory / "task.json"
            if directory.is_dir() and task_file.is_file():
                summary = read_json(task_file)
                summary["effective_status"] = self.status(directory.name)["effective_status"]
                summary["active_lease_count"] = len(self.active_leases(directory.name))
                tasks.append(summary)
        return tasks
