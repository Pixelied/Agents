from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .core import Workspace, WorkspaceError


def print_json(value: object) -> None:
    print(json.dumps(value, indent=2, sort_keys=True))


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description="Coordinate AI agents through a Git-native workspace")
    root.add_argument("--root", default=".", help="Workspace root (default: current directory)")
    commands = root.add_subparsers(dest="command", required=True)

    commands.add_parser("init", help="Create runtime directories")
    commands.add_parser("agent-list", help="List registered agent instances")
    commands.add_parser("task-list", help="List tasks with derived state")

    register = commands.add_parser("register", help="Register a unique agent instance")
    register.add_argument("--id", required=True)
    register.add_argument("--provider", required=True)
    register.add_argument("--model", required=True)
    register.add_argument("--description", default="")
    register.add_argument("--capability", action="append", default=[])

    task = commands.add_parser("task-create", help="Create a task workspace")
    task.add_argument("--id", required=True)
    task.add_argument("--title", required=True)
    task.add_argument("--created-by", required=True)
    task.add_argument("--objective", required=True)
    task.add_argument("--scope", action="append", required=True)
    task.add_argument("--accept", action="append", default=[])
    task.add_argument("--priority", choices=["low", "normal", "high", "critical"], default="normal")

    claim = commands.add_parser("claim", help="Claim one exclusive task scope")
    claim.add_argument("--task", required=True)
    claim.add_argument("--scope", required=True)
    claim.add_argument("--agent", required=True)
    claim.add_argument("--ttl", type=int, default=60)
    claim.add_argument("--intent", default="")

    heartbeat = commands.add_parser("heartbeat", help="Extend an owned active lease")
    heartbeat.add_argument("--task", required=True)
    heartbeat.add_argument("--scope", required=True)
    heartbeat.add_argument("--agent", required=True)
    heartbeat.add_argument("--ttl", type=int, default=60)

    release = commands.add_parser("release", help="Release an owned scope")
    release.add_argument("--task", required=True)
    release.add_argument("--scope", required=True)
    release.add_argument("--agent", required=True)
    release.add_argument("--reason", default="completed")

    event = commands.add_parser("event", help="Append a task event")
    event.add_argument("--task", required=True)
    event.add_argument("--agent", required=True)
    event.add_argument("--type", required=True)
    event.add_argument("--message", required=True)
    event.add_argument("--data", default="{}", help="JSON object")

    handoff = commands.add_parser("handoff", help="Create a structured agent handoff")
    handoff.add_argument("--task", required=True)
    handoff.add_argument("--from-agent", required=True)
    handoff.add_argument("--to-agent", required=True)
    handoff.add_argument("--summary", required=True)
    handoff.add_argument("--completed", action="append", default=[])
    handoff.add_argument("--remaining", action="append", default=[])
    handoff.add_argument("--next-action", required=True)
    handoff.add_argument("--file", action="append", default=[])
    handoff.add_argument("--verification", action="append", default=[])
    handoff.add_argument("--risk", action="append", default=[])

    state = commands.add_parser("task-state", help="Append a derived task-state change")
    state.add_argument("--task", required=True)
    state.add_argument("--agent", required=True)
    state.add_argument("--state", choices=["open", "blocked", "completed", "cancelled"], required=True)
    state.add_argument("--message", required=True)

    status = commands.add_parser("status", help="Show derived task state")
    status.add_argument("--task", required=True)

    commands.add_parser("validate", help="Validate the complete workspace")
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    ws = Workspace(Path(args.root))
    try:
        if args.command == "init":
            ws.initialize()
            print_json({"ok": True, "root": str(ws.root)})
        elif args.command == "agent-list":
            print_json(ws.list_agents())
        elif args.command == "task-list":
            print_json(ws.list_tasks())
        elif args.command == "register":
            print_json(ws.register_agent(args.id, args.provider, args.model, args.capability, args.description))
        elif args.command == "task-create":
            print_json(
                ws.create_task(
                    args.id, args.title, args.created_by, args.objective,
                    args.scope, args.accept, args.priority,
                )
            )
        elif args.command == "claim":
            print_json(ws.claim_scope(args.task, args.scope, args.agent, args.ttl, args.intent))
        elif args.command == "heartbeat":
            print_json(ws.heartbeat(args.task, args.scope, args.agent, args.ttl))
        elif args.command == "release":
            print_json(ws.release_scope(args.task, args.scope, args.agent, args.reason))
        elif args.command == "event":
            data = json.loads(args.data)
            if not isinstance(data, dict):
                raise WorkspaceError("--data must be a JSON object")
            _, payload = ws.add_event(args.task, args.agent, args.type, args.message, data)
            print_json(payload)
        elif args.command == "handoff":
            _, payload = ws.create_handoff(
                args.task, args.from_agent, args.to_agent, args.summary,
                args.completed, args.remaining, args.next_action,
                args.file, args.verification, args.risk,
            )
            print_json(payload)
        elif args.command == "task-state":
            print_json(ws.set_task_state(args.task, args.agent, args.state, args.message))
        elif args.command == "status":
            print_json(ws.status(args.task))
        elif args.command == "validate":
            errors = ws.validate()
            if errors:
                print_json({"ok": False, "errors": errors})
                return 1
            print_json({"ok": True, "errors": []})
        return 0
    except (WorkspaceError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
