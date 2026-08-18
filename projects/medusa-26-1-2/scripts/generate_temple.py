from __future__ import annotations

import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "datapacks/medusa/data/medusa/function/dungeon/build_generated.mcfunction"

FIXED_MAZE_V1 = [
    "#####################",
    "#E....#.......#....L#",
    "#####.#.#####.#.###.#",
    "#.....#.#...#.#...#.#",
    "#.#####.#A#.#.###.#.#",
    "#.......#.#.#.....#.#",
    "#.#######.#.#######.#",
    "#...B.....#.....T...#",
    "###.#######.#######.#",
    "#...#.....#.....#...#",
    "#.###.###.#.###.#.###",
    "#L....#C..#...#.....#",
    "#####.#####.#.#####.#",
    "#...........#.....I.#",
    "#####################",
]

TILE = 4
BASE_X = -42
BASE_Y = -18
BASE_Z = 16
REQUIRED = "EABCI"
SPECIAL_BLOCKS = {
    "E": "minecraft:chiseled_stone_bricks",
    "A": "minecraft:cut_copper",
    "B": "minecraft:oxidized_cut_copper",
    "C": "minecraft:chiseled_deepslate",
    "L": "minecraft:gilded_blackstone",
    "T": "minecraft:redstone_block",
    "I": "minecraft:lodestone",
}


def validate_map() -> None:
    widths = {len(row) for row in FIXED_MAZE_V1}
    if len(widths) != 1:
        raise ValueError(f"maze rows have inconsistent widths: {sorted(widths)}")
    for symbol in REQUIRED:
        count = sum(row.count(symbol) for row in FIXED_MAZE_V1)
        if count != 1:
            raise ValueError(f"expected exactly one {symbol}, found {count}")


def rel(n: int) -> str:
    return "~" if n == 0 else f"~{n}"


def runs(row: str):
    start = 0
    wall = row[0] == "#"
    for idx in range(1, len(row) + 1):
        next_wall = idx < len(row) and row[idx] == "#"
        if idx == len(row) or next_wall != wall:
            yield start, idx - 1, wall
            if idx < len(row):
                start = idx
                wall = next_wall


def render() -> str:
    validate_map()
    lines = [
        "# generated from FIXED_MAZE_V1; run scripts/generate_temple.py to update",
        "function medusa:dungeon/build_surface",
        "function medusa:dungeon/build_arena",
        "# fixed underground labyrinth",
    ]
    for z, row in enumerate(FIXED_MAZE_V1):
        z1 = BASE_Z + z * TILE
        z2 = z1 + TILE - 1
        for x_start, x_end, wall in runs(row):
            x1 = BASE_X + x_start * TILE
            x2 = BASE_X + (x_end + 1) * TILE - 1
            if wall:
                lines.append(
                    f"fill {rel(x1)} {rel(BASE_Y)} {rel(z1)} {rel(x2)} {rel(BASE_Y + 3)} {rel(z2)} minecraft:stone_bricks"
                )
            else:
                lines.append(
                    f"fill {rel(x1)} {rel(BASE_Y)} {rel(z1)} {rel(x2)} {rel(BASE_Y)} {rel(z2)} minecraft:polished_deepslate"
                )
                lines.append(
                    f"fill {rel(x1)} {rel(BASE_Y + 1)} {rel(z1)} {rel(x2)} {rel(BASE_Y + 3)} {rel(z2)} minecraft:air"
                )
        for x, cell in enumerate(row):
            if cell in SPECIAL_BLOCKS:
                x1 = BASE_X + x * TILE
                lines.append(
                    f"setblock {rel(x1 + 1)} {rel(BASE_Y)} {rel(z1 + 1)} {SPECIAL_BLOCKS[cell]}"
                )
    lines.extend([
        "# deterministic age/atmosphere accents",
        f"setblock {rel(BASE_X + 9)} {rel(BASE_Y + 3)} {rel(BASE_Z + 5)} minecraft:soul_lantern[hanging=true]",
        f"setblock {rel(BASE_X + 31)} {rel(BASE_Y + 1)} {rel(BASE_Z + 29)} minecraft:cobweb",
        f"setblock {rel(BASE_X + 55)} {rel(BASE_Y + 3)} {rel(BASE_Z + 45)} minecraft:soul_lantern[hanging=true]",
        f"setblock {rel(BASE_X + 7)} {rel(BASE_Y + 2)} {rel(BASE_Z + 6)} minecraft:vine[north=true]",
        f"setblock {rel(BASE_X + 77)} {rel(BASE_Y + 2)} {rel(BASE_Z + 53)} minecraft:vine[south=true]",
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
            print(f"generated output missing: {OUT.relative_to(ROOT)}", file=sys.stderr)
            return 1
        if OUT.read_text(encoding="utf-8") != text:
            print(f"generated output is stale: {OUT.relative_to(ROOT)}", file=sys.stderr)
            return 1
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
