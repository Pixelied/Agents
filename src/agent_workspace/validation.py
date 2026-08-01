from __future__ import annotations

from pathlib import Path

from .registry import AGENT_STATES
from .storage import WorkspaceError, parse_time, read_json, utc_now


class ValidationMixin:
    root: Path

    def validate(self) -> list[str]:
        errors: list[str] = []
        if not self.agents_dir.is_dir():
            errors.append("Missing agents/ directory")
        if not self.tasks_dir.is_dir():
            errors.append("Missing tasks/ directory")

        known_agents: set[str] = set()
        agent_states: dict[str, str] = {}
        if self.agents_dir.is_dir():
            for directory in sorted(self.agents_dir.iterdir()):
                if not directory.is_dir() or directory.name.startswith("_"):
                    continue
                profile_path = directory / "profile.json"
                try:
                    profile = read_json(profile_path)
                    required = {
                        "schema_version",
                        "protocol_version",
                        "agent_id",
                        "provider",
                        "model",
                        "state",
                    }
                    missing = sorted(required - profile.keys())
                    if missing:
                        errors.append(f"{profile_path}: missing fields {', '.join(missing)}")
                    if profile.get("agent_id") != directory.name:
                        errors.append(f"{profile_path}: agent_id must match directory name")
                    state = profile.get("state")
                    if state not in AGENT_STATES:
                        errors.append(f"{profile_path}: invalid state {state!r}")
                    for field in ("registered_at", "state_updated_at", "retired_at"):
                        if profile.get(field):
                            try:
                                parse_time(profile[field])
                            except (TypeError, ValueError):
                                errors.append(f"{profile_path}: invalid {field}")
                    known_agents.add(directory.name)
                    agent_states[directory.name] = str(state)
                except WorkspaceError as exc:
                    errors.append(str(exc))

        now = utc_now()
        if self.tasks_dir.is_dir():
            for directory in sorted(self.tasks_dir.iterdir()):
                if not directory.is_dir() or directory.name.startswith("_"):
                    continue
                task_path = directory / "task.json"
                try:
                    task = read_json(task_path)
                    required = {
                        "schema_version",
                        "protocol_version",
                        "task_id",
                        "title",
                        "objective",
                        "created_by",
                        "scopes",
                        "status",
                    }
                    missing = sorted(required - task.keys())
                    if missing:
                        errors.append(f"{task_path}: missing fields {', '.join(missing)}")
                    if task.get("task_id") != directory.name:
                        errors.append(f"{task_path}: task_id must match directory name")
                    if task.get("created_by") not in known_agents:
                        errors.append(f"{task_path}: unknown creator {task.get('created_by')!r}")
                    declared_scopes = set(task.get("scopes", []))
                except WorkspaceError as exc:
                    errors.append(str(exc))
                    declared_scopes = set()

                lease_dir = directory / "leases"
                if lease_dir.is_dir():
                    for lease_path in sorted(lease_dir.glob("*.json")):
                        try:
                            lease = read_json(lease_path)
                            owner = lease.get("owner")
                            if owner not in known_agents:
                                errors.append(f"{lease_path}: unknown agent {owner!r}")
                            if lease.get("scope") not in declared_scopes:
                                errors.append(
                                    f"{lease_path}: undeclared scope {lease.get('scope')!r}"
                                )
                            expires_at = parse_time(lease["expires_at"])
                            if (
                                lease.get("state") == "active"
                                and expires_at > now
                                and agent_states.get(str(owner)) in {"offline", "retired"}
                            ):
                                errors.append(
                                    f"{lease_path}: active lease owned by "
                                    f"{agent_states.get(str(owner))} agent {owner!r}"
                                )
                        except (WorkspaceError, KeyError, ValueError, TypeError) as exc:
                            errors.append(f"{lease_path}: invalid lease: {exc}")

                for collection in ("events", "handoffs"):
                    target = directory / collection
                    if not target.is_dir():
                        errors.append(f"{directory}: missing {collection}/ directory")
                        continue
                    for item in target.glob("*.json"):
                        try:
                            record = read_json(item)
                            if (
                                collection == "events"
                                and record.get("agent_id") not in known_agents
                            ):
                                errors.append(
                                    f"{item}: unknown agent {record.get('agent_id')!r}"
                                )
                            if collection == "handoffs":
                                for field in ("from_agent", "to_agent"):
                                    if record.get(field) not in known_agents:
                                        errors.append(
                                            f"{item}: unknown {field} {record.get(field)!r}"
                                        )
                        except WorkspaceError as exc:
                            errors.append(str(exc))
        return errors
