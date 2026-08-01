from __future__ import annotations

import uuid
from datetime import timedelta
from pathlib import Path
from typing import Any, Iterable

from .storage import (
    PROTOCOL_VERSION,
    WorkspaceError,
    ensure_scope,
    exclusive_path_lock,
    iso,
    parse_time,
    read_json,
    scope_filename,
    utc_now,
    write_json,
)


class CoordinationMixin:
    root: Path

    def _lease_path(self, task_id: str, scope: str) -> Path:
        return self.task_path(task_id) / "leases" / scope_filename(ensure_scope(scope))

    def claim_scope(
        self,
        task_id: str,
        scope: str,
        agent_id: str,
        ttl_minutes: int = 60,
        intent: str = "",
    ) -> dict[str, Any]:
        task = self.require_task(task_id)
        self.require_agent(agent_id)
        scope = ensure_scope(scope)
        if scope not in task["scopes"]:
            raise WorkspaceError(f"Scope {scope!r} is not declared by task {task_id!r}")
        if ttl_minutes < 5 or ttl_minutes > 1440:
            raise WorkspaceError("Lease TTL must be between 5 and 1440 minutes")
        path = self._lease_path(task_id, scope)
        with exclusive_path_lock(path):
            now = utc_now()
            generation = 1
            claimed_at = iso(now)
            if path.exists():
                current = read_json(path)
                generation = int(current.get("generation", 0)) + 1
                state = current.get("state")
                expires_at = parse_time(current["expires_at"])
                if state == "active" and expires_at > now and current.get("owner") != agent_id:
                    raise WorkspaceError(
                        f"Scope {scope!r} is actively leased by {current.get('owner')!r} until "
                        f"{current['expires_at']}"
                    )
                if state == "active" and current.get("owner") == agent_id:
                    generation = int(current.get("generation", 1))
                    claimed_at = current.get("claimed_at", claimed_at)
            lease = {
                "schema_version": 1,
                "protocol_version": PROTOCOL_VERSION,
                "task_id": task_id,
                "scope": scope,
                "owner": agent_id,
                "state": "active",
                "intent": intent.strip(),
                "claimed_at": claimed_at,
                "heartbeat_at": iso(now),
                "expires_at": iso(now + timedelta(minutes=ttl_minutes)),
                "generation": generation,
            }
            write_json(path, lease)
        self.add_event(task_id, agent_id, "scope_claimed", f"Claimed {scope}", {"scope": scope})
        return lease

    def heartbeat(
        self, task_id: str, scope: str, agent_id: str, ttl_minutes: int = 60
    ) -> dict[str, Any]:
        if ttl_minutes < 5 or ttl_minutes > 1440:
            raise WorkspaceError("Lease TTL must be between 5 and 1440 minutes")
        path = self._lease_path(task_id, scope)
        with exclusive_path_lock(path):
            lease = read_json(path)
            now = utc_now()
            if lease.get("state") != "active":
                raise WorkspaceError(f"Scope {scope!r} is not active")
            if lease.get("owner") != agent_id:
                raise WorkspaceError(f"Scope {scope!r} is owned by {lease.get('owner')!r}")
            if parse_time(lease["expires_at"]) <= now:
                raise WorkspaceError(f"Lease for scope {scope!r} has expired and must be reclaimed")
            lease["heartbeat_at"] = iso(now)
            lease["expires_at"] = iso(now + timedelta(minutes=ttl_minutes))
            write_json(path, lease)
        return lease

    def release_scope(
        self, task_id: str, scope: str, agent_id: str, reason: str = "completed"
    ) -> dict[str, Any]:
        path = self._lease_path(task_id, scope)
        with exclusive_path_lock(path):
            lease = read_json(path)
            if lease.get("owner") != agent_id:
                raise WorkspaceError(f"Scope {scope!r} is owned by {lease.get('owner')!r}")
            if lease.get("state") != "active":
                raise WorkspaceError(f"Scope {scope!r} is already {lease.get('state')!r}")
            lease["state"] = "released"
            lease["released_at"] = iso()
            lease["release_reason"] = reason.strip() or "completed"
            write_json(path, lease)
        self.add_event(
            task_id,
            agent_id,
            "scope_released",
            f"Released {scope}: {lease['release_reason']}",
            {"scope": scope},
        )
        return lease

    def add_event(
        self,
        task_id: str,
        agent_id: str,
        event_type: str,
        message: str,
        data: dict[str, Any] | None = None,
    ) -> tuple[Path, dict[str, Any]]:
        self.require_task(task_id)
        event_type = event_type.strip().lower().replace(" ", "_")
        if not event_type or not message.strip():
            raise WorkspaceError("Event type and message are required")
        now = utc_now()
        event_id = uuid.uuid4().hex
        payload = {
            "schema_version": 1,
            "protocol_version": PROTOCOL_VERSION,
            "event_id": event_id,
            "task_id": task_id,
            "agent_id": agent_id,
            "type": event_type,
            "message": message.strip(),
            "data": data or {},
            "created_at": iso(now),
        }
        filename = now.strftime("%Y%m%dT%H%M%S.%fZ") + f"-{event_id}.json"
        path = self.task_path(task_id) / "events" / filename
        if path.exists():
            raise WorkspaceError(f"Refusing to overwrite append-only event {path}")
        write_json(path, payload)
        return path, payload

    def create_handoff(
        self,
        task_id: str,
        from_agent: str,
        to_agent: str,
        summary: str,
        completed: Iterable[str],
        remaining: Iterable[str],
        next_action: str,
        files_changed: Iterable[str] = (),
        verification: Iterable[str] = (),
        risks: Iterable[str] = (),
    ) -> tuple[Path, dict[str, Any]]:
        self.require_task(task_id)
        self.require_agent(from_agent)
        self.require_agent(to_agent)
        if not summary.strip() or not next_action.strip():
            raise WorkspaceError("Handoff summary and next action are required")
        now = utc_now()
        handoff_id = uuid.uuid4().hex
        payload = {
            "schema_version": 1,
            "protocol_version": PROTOCOL_VERSION,
            "handoff_id": handoff_id,
            "task_id": task_id,
            "from_agent": from_agent,
            "to_agent": to_agent,
            "summary": summary.strip(),
            "completed": [item.strip() for item in completed if item.strip()],
            "remaining": [item.strip() for item in remaining if item.strip()],
            "next_action": next_action.strip(),
            "files_changed": [item.strip() for item in files_changed if item.strip()],
            "verification": [item.strip() for item in verification if item.strip()],
            "risks": [item.strip() for item in risks if item.strip()],
            "created_at": iso(now),
        }
        filename = now.strftime("%Y%m%dT%H%M%S.%fZ") + f"-{handoff_id}.json"
        path = self.task_path(task_id) / "handoffs" / filename
        write_json(path, payload)
        inbox = self.agent_path(to_agent) / "inbox" / f"handoff-{task_id}-{handoff_id}.json"
        write_json(inbox, payload)
        self.add_event(
            task_id,
            from_agent,
            "handoff_created",
            f"Handoff to {to_agent}: {summary.strip()}",
            {"handoff_id": handoff_id, "to_agent": to_agent},
        )
        return path, payload

    def set_task_state(
        self, task_id: str, agent_id: str, state: str, message: str
    ) -> dict[str, Any]:
        self.require_task(task_id)
        self.require_agent(agent_id)
        if state not in {"open", "blocked", "completed", "cancelled"}:
            raise WorkspaceError(f"Invalid task state {state!r}")
        _, event = self.add_event(
            task_id, agent_id, "task_state_changed", message, {"state": state}
        )
        return event

    def active_leases(self, task_id: str) -> list[dict[str, Any]]:
        self.require_task(task_id)
        now = utc_now()
        leases: list[dict[str, Any]] = []
        for path in sorted((self.task_path(task_id) / "leases").glob("*.json")):
            try:
                lease = read_json(path)
                if lease.get("state") == "active" and parse_time(lease["expires_at"]) > now:
                    leases.append(lease)
            except (WorkspaceError, KeyError, ValueError):
                continue
        return leases

    def status(self, task_id: str) -> dict[str, Any]:
        task = self.require_task(task_id)
        events = []
        for path in sorted((self.task_path(task_id) / "events").glob("*.json")):
            try:
                events.append(read_json(path))
            except WorkspaceError:
                pass
        handoffs = []
        for path in sorted((self.task_path(task_id) / "handoffs").glob("*.json")):
            try:
                handoffs.append(read_json(path))
            except WorkspaceError:
                pass
        effective_status = task.get("status", "open")
        for event in events:
            if event.get("type") == "task_state_changed":
                state = event.get("data", {}).get("state")
                if state in {"open", "blocked", "completed", "cancelled"}:
                    effective_status = state
        return {
            "task": task,
            "effective_status": effective_status,
            "active_leases": self.active_leases(task_id),
            "recent_events": events[-20:],
            "recent_handoffs": handoffs[-10:],
        }
