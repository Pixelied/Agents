from __future__ import annotations

import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "datapacks/medusa/data/medusa/function/dungeon/build_generated.mcfunction"

SHIFTING_LABYRINTH_V3 = "runtime-procedural-13x13"
MAZE_ROWS = 13
MAZE_COLS = 13
CELL_PITCH = 7
MAZE_BASE_X = -44
MAZE_BASE_Y = -18
MAZE_BASE_Z = 30
PASSAGE_WIDTH = 3
WALL_HEIGHT = 8


def render() -> str:
    return "\n".join([
        "# generated from SHIFTING_LABYRINTH_V3; run scripts/generate_temple.py to update",
        "# continuous route: surface -> descent -> shifting labyrinth -> sanctum -> arena",
        "function medusa:dungeon/build_surface",
        "function medusa:dungeon/maze/build_shell",
        "function medusa:dungeon/maze/build_roofs",
        "function medusa:dungeon/maze/build_landmarks",
        "function medusa:dungeon/maze/build_containment",
        "function medusa:dungeon/build_descent",
        "function medusa:dungeon/build_sanctum",
        "function medusa:dungeon/build_arena",
        "function medusa:dungeon/build_arena_approach",
        "",
    ])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    text = render()
    if args.check:
        if not OUT.exists():
            print(f"generated output missing: {OUT}", file=sys.stderr)
            return 1
        if OUT.read_text(encoding="utf-8") != text:
            print(f"generated output is stale: {OUT}", file=sys.stderr)
            return 1
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
