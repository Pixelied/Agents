from __future__ import annotations
import argparse
from pathlib import Path
import random
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "datapacks/medusa/data/medusa/function/dungeon/build_generated.mcfunction"

CINEMATIC_MAZE_V2 = "dfs-17x17-seed-26012"
MAZE_WIDTH = 17
MAZE_DEPTH = 17
TILE = 3
BASE_X = -42
BASE_Y = -18
BASE_Z = 38
SEED = 26012


def rel(n: int) -> str:
    return "~" if n == 0 else f"~{n}"


def build_maze() -> list[list[str]]:
    grid = [["#"] * MAZE_WIDTH for _ in range(MAZE_DEPTH)]
    rng = random.Random(SEED)
    start = (1, MAZE_WIDTH - 2)
    stack = [start]
    grid[start[0]][start[1]] = "."
    while stack:
        z, x = stack[-1]
        choices = []
        for dz, dx in ((0, 2), (0, -2), (2, 0), (-2, 0)):
            nz, nx = z + dz, x + dx
            if 1 <= nz < MAZE_DEPTH - 1 and 1 <= nx < MAZE_WIDTH - 1 and grid[nz][nx] == "#":
                choices.append((nz, nx, dz, dx))
        if not choices:
            stack.pop()
            continue
        nz, nx, dz, dx = rng.choice(choices)
        grid[z + dz // 2][x + dx // 2] = "."
        grid[nz][nx] = "."
        stack.append((nz, nx))

    loop_candidates = []
    for z in range(1, MAZE_DEPTH - 1):
        for x in range(1, MAZE_WIDTH - 1):
            if grid[z][x] != "#":
                continue
            horizontal = grid[z][x - 1] == "." and grid[z][x + 1] == "."
            vertical = grid[z - 1][x] == "." and grid[z + 1][x] == "."
            if horizontal or vertical:
                loop_candidates.append((z, x))
    rng.shuffle(loop_candidates)
    for z, x in loop_candidates[:12]:
        grid[z][x] = "."

    # East-side entrance from the Averted Eyes chamber.
    grid[1][MAZE_WIDTH - 2] = "."
    grid[1][MAZE_WIDTH - 1] = "."
    return grid


def open_runs(row: list[str]):
    x = 0
    while x < len(row):
        if row[x] != ".":
            x += 1
            continue
        start = x
        while x + 1 < len(row) and row[x + 1] == ".":
            x += 1
        yield start, x
        x += 1


def render() -> str:
    grid = build_maze()
    x2 = BASE_X + MAZE_WIDTH * TILE - 1
    z2 = BASE_Z + MAZE_DEPTH * TILE - 1
    lines = [
        "# generated from CINEMATIC_MAZE_V2; run scripts/generate_temple.py to update",
        "# continuous route: surface -> descent -> Averted Eyes -> labyrinth/Borrowed Gaze -> Blind Passage -> sanctum -> arena",
        "function medusa:dungeon/build_surface",
        "function medusa:dungeon/build_descent",
        "# cinematic underground labyrinth shell",
        f"fill {rel(BASE_X)} {rel(BASE_Y)} {rel(BASE_Z)} {rel(x2)} {rel(BASE_Y + 7)} {rel(z2)} minecraft:stone_bricks",
    ]

    for z, row in enumerate(grid):
        z1 = BASE_Z + z * TILE
        z3 = z1 + TILE - 1
        for x_start, x_end in open_runs(row):
            x1 = BASE_X + x_start * TILE
            x3 = BASE_X + (x_end + 1) * TILE - 1
            floor = "minecraft:polished_deepslate" if (z + x_start) % 3 else "minecraft:cracked_deepslate_tiles"
            lines.append(f"fill {rel(x1)} {rel(BASE_Y)} {rel(z1)} {rel(x3)} {rel(BASE_Y)} {rel(z3)} {floor}")
            lines.append(f"fill {rel(x1)} {rel(BASE_Y + 1)} {rel(z1)} {rel(x3)} {rel(BASE_Y + 6)} {rel(z3)} minecraft:air")

    # Deterministic landmarks help players orient without revealing the solution.
    open_cells = [(z, x) for z, row in enumerate(grid) for x, cell in enumerate(row) if cell == "."]
    for i, (z, x) in enumerate(open_cells):
        cx = BASE_X + x * TILE + 1
        cz = BASE_Z + z * TILE + 1
        if i % 17 == 3:
            lines.append(f"setblock {rel(cx)} {rel(BASE_Y + 6)} {rel(cz)} minecraft:soul_lantern[hanging=true]")
        elif i % 23 == 7:
            lines.append(f"setblock {rel(cx)} {rel(BASE_Y + 1)} {rel(cz)} minecraft:cobweb")
        elif i % 29 == 11:
            lines.append(f"setblock {rel(cx)} {rel(BASE_Y + 1)} {rel(cz)} minecraft:moss_carpet")

    lines.extend([
        "# authored chambers carve into the maze after the shell so they read as destinations",
        "function medusa:dungeon/build_puzzle_averted_room",
        "function medusa:dungeon/build_puzzle_borrowed_room",
        "function medusa:dungeon/build_puzzle_blind_room",
        "function medusa:dungeon/build_sanctum",
        "function medusa:dungeon/build_arena",
        "function medusa:dungeon/build_arena_approach",
        "",
    ])
    return "\n".join(lines)


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
