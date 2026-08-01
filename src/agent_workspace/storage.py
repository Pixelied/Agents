from __future__ import annotations

import json
import os
import re
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator
from urllib.parse import quote

PROTOCOL_VERSION = "1.0"
ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
SCOPE_PATTERN = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9._/-]{0,127}$")


class WorkspaceError(RuntimeError):
    """Raised when a workspace operation would violate the protocol."""


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso(dt: datetime | None = None) -> str:
    return (dt or utc_now()).isoformat(timespec="seconds")


def parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + f".{uuid.uuid4().hex}.tmp")
    temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def read_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise WorkspaceError(f"Missing required file: {path}") from exc
    except json.JSONDecodeError as exc:
        raise WorkspaceError(f"Invalid JSON in {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise WorkspaceError(f"Expected a JSON object in {path}")
    return data


def ensure_identifier(value: str, label: str) -> str:
    if not ID_PATTERN.fullmatch(value):
        raise WorkspaceError(
            f"Invalid {label} {value!r}; use 1-64 lowercase letters, digits, '.', '_', or '-'"
        )
    return value


def ensure_scope(value: str) -> str:
    if not SCOPE_PATTERN.fullmatch(value) or ".." in value or value.startswith("/"):
        raise WorkspaceError(
            f"Invalid scope {value!r}; use a relative path-like name without '..'"
        )
    return value


def scope_filename(scope: str) -> str:
    return quote(scope, safe="") + ".json"


@contextmanager
def exclusive_path_lock(target: Path) -> Iterator[None]:
    """Serialize local updates to a deterministic coordination file."""
    lock = target.with_suffix(target.suffix + ".lock")
    lock.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as exc:
        raise WorkspaceError(f"Concurrent update in progress for {target}") from exc
    try:
        os.write(descriptor, f"pid={os.getpid()}\ncreated_at={iso()}\n".encode())
        os.close(descriptor)
        yield
    finally:
        try:
            lock.unlink()
        except FileNotFoundError:
            pass
