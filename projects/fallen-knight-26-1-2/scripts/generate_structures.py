from __future__ import annotations

import gzip
import pathlib
import struct
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "datapacks/fallen_knight/data/fallen_knight/structure"
DATA_VERSION = 4790


def s16(text: str) -> bytes:
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + s16(name) + payload


def p_byte(v: int) -> bytes: return struct.pack(">b", v)
def p_short(v: int) -> bytes: return struct.pack(">h", v)
def p_int(v: int) -> bytes: return struct.pack(">i", v)
def p_double(v: float) -> bytes: return struct.pack(">d", v)
def p_string(v: str) -> bytes: return s16(v)
def p_compound(tags: list[bytes]) -> bytes: return b"".join(tags) + b"\x00"
def p_list(tag_type: int, payloads: list[bytes]) -> bytes:
    return bytes([tag_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def t_byte(name: str, v: int) -> bytes: return named(1, name, p_byte(v))
def t_short(name: str, v: int) -> bytes: return named(2, name, p_short(v))
def t_int(name: str, v: int) -> bytes: return named(3, name, p_int(v))
def t_string(name: str, v: str) -> bytes: return named(8, name, p_string(v))
def t_compound(name: str, tags: list[bytes]) -> bytes: return named(10, name, p_compound(tags))
def t_list(name: str, tag_type: int, payloads: list[bytes]) -> bytes: return named(9, name, p_list(tag_type, payloads))


def p_int_list(values: list[int]) -> bytes:
    return p_list(3, [p_int(v) for v in values])


def p_double_list(values: list[float]) -> bytes:
    return p_list(6, [p_double(v) for v in values])


def p_string_list(values: list[str]) -> bytes:
    return p_list(8, [p_string(v) for v in values])


@dataclass(frozen=True)
class State:
    name: str
    props: tuple[tuple[str, str], ...] = ()


class Structure:
    def __init__(self, size: tuple[int, int, int]):
        self.size = size
        self.palette: list[State] = []
        self.palette_index: dict[State, int] = {}
        self.blocks: dict[tuple[int, int, int], tuple[State, list[bytes] | None]] = {}
        self.entities: list[tuple[list[float], list[int], list[bytes]]] = []

    def state(self, name: str, **props: str) -> State:
        st = State(name, tuple(sorted((k, str(v)) for k, v in props.items())))
        if st not in self.palette_index:
            self.palette_index[st] = len(self.palette)
            self.palette.append(st)
        return st

    def set(self, x: int, y: int, z: int, name: str, nbt: list[bytes] | None = None, **props: str) -> None:
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            raise ValueError((x, y, z, self.size))
        self.blocks[(x, y, z)] = (self.state(name, **props), nbt)

    def fill(self, x1, y1, z1, x2, y2, z2, name: str, **props: str) -> None:
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                for x in range(x1, x2 + 1):
                    self.set(x, y, z, name, **props)

    def entity(self, x: float, y: float, z: float, entity_id: str, tags: list[str] | None = None, extra: list[bytes] | None = None) -> None:
        nbt = [t_string("id", entity_id)]
        if tags:
            nbt.append(t_list("Tags", 8, [p_string(v) for v in tags]))
        if extra:
            nbt.extend(extra)
        self.entities.append(([x, y, z], [int(x), int(y), int(z)], nbt))

    def chest(self, x: int, y: int, z: int, loot_table: str, facing: str = "north") -> None:
        self.set(x, y, z, "minecraft:chest", [t_string("id", "minecraft:chest"), t_string("LootTable", loot_table)], facing=facing, type="single", waterlogged="false")

    def spawner(self, x: int, y: int, z: int, mob: str) -> None:
        spawn_data = t_compound("SpawnData", [t_compound("entity", [t_string("id", mob), t_byte("PersistenceRequired", 1)])])
        nbt = [
            t_string("id", "minecraft:mob_spawner"),
            t_short("Delay", 40), t_short("MinSpawnDelay", 300), t_short("MaxSpawnDelay", 500),
            t_short("SpawnCount", 2), t_short("MaxNearbyEntities", 4), t_short("RequiredPlayerRange", 12), t_short("SpawnRange", 3),
            spawn_data,
        ]
        self.set(x, y, z, "minecraft:spawner", nbt)

    def write(self, path: pathlib.Path) -> None:
        # Stable palette order follows first use; stable blocks are coordinate sorted.
        palette_payloads = []
        for st in self.palette:
            tags = [t_string("Name", st.name)]
            if st.props:
                tags.append(t_compound("Properties", [t_string(k, v) for k, v in st.props]))
            palette_payloads.append(p_compound(tags))

        block_payloads = []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            st, nbt = self.blocks[pos]
            tags = [t_list("pos", 3, [p_int(v) for v in pos]), t_int("state", self.palette_index[st])]
            if nbt:
                tags.append(t_compound("nbt", nbt))
            block_payloads.append(p_compound(tags))

        entity_payloads = []
        for pos, block_pos, nbt in self.entities:
            entity_payloads.append(p_compound([
                t_list("pos", 6, [p_double(v) for v in pos]),
                t_list("blockPos", 3, [p_int(v) for v in block_pos]),
                t_compound("nbt", nbt),
            ]))

        root = p_compound([
            t_list("size", 3, [p_int(v) for v in self.size]),
            t_list("entities", 10, entity_payloads),
            t_list("blocks", 10, block_payloads),
            t_list("palette", 10, palette_payloads),
            t_int("DataVersion", DATA_VERSION),
        ])
        raw = bytes([10]) + s16("") + root
        path.parent.mkdir(parents=True, exist_ok=True)
        with gzip.GzipFile(filename="", mode="wb", fileobj=path.open("wb"), mtime=0) as gz:
            gz.write(raw)


def ruin_block(x: int, z: int, y: int = 0) -> str:
    r = (x * 37 + z * 19 + y * 13) % 17
    if r in (0, 1): return "minecraft:mossy_stone_bricks"
    if r in (2, 3, 4): return "minecraft:cracked_stone_bricks"
    return "minecraft:stone_bricks"


def build_castle() -> Structure:
    s = Structure((47, 14, 47))
    # Courtyard floor and approach road.
    for z in range(8, 39):
        for x in range(8, 39):
            s.set(x, 0, z, ruin_block(x, z))
    for z in range(1, 9):
        for x in range(20, 27):
            s.set(x, 0, z, ruin_block(x, z))

    # Outer curtain walls with deliberately broken crenellations.
    for x in range(1, 46):
        if 20 <= x <= 26:
            continue
        height_s = 4 + ((x * 5) % 3)
        height_n = 4 + ((x * 7) % 4)
        for y in range(0, height_s): s.set(x, y, 1, ruin_block(x, 1, y))
        for y in range(0, height_n): s.set(x, y, 45, ruin_block(x, 45, y))
    for z in range(2, 45):
        height_w = 4 + ((z * 3) % 4)
        height_e = 4 + ((z * 11) % 3)
        for y in range(0, height_w): s.set(1, y, z, ruin_block(1, z, y))
        for y in range(0, height_e): s.set(45, y, z, ruin_block(45, z, y))

    # Four ruined corner towers.
    for ox, oz in ((2,2),(38,2),(2,38),(38,38)):
        for y in range(0, 9):
            for i in range(0, 7):
                if (y + i + ox + oz) % 11 != 0:
                    s.set(ox+i, y, oz, ruin_block(ox+i, oz, y))
                    s.set(ox+i, y, oz+6, ruin_block(ox+i, oz+6, y))
                    s.set(ox, y, oz+i, ruin_block(ox, oz+i, y))
                    s.set(ox+6, y, oz+i, ruin_block(ox+6, oz+i, y))
        s.fill(ox+1, 0, oz+1, ox+5, 0, oz+5, "minecraft:cobblestone")

    # Gatehouse framing and dark-oak gate beams.
    for x in list(range(15,20)) + list(range(27,32)):
        for z in range(1, 7):
            for y in range(1, 6):
                if z in (1,6) or x in (15,19,27,31):
                    s.set(x, y, z, ruin_block(x, z, y))
    for x in (19,27):
        s.fill(x,1,1,x,6,7,"minecraft:dark_oak_log",axis="y")
    s.fill(20,6,1,26,6,1,"minecraft:dark_oak_log",axis="x")
    # Heraldic red-black slash over the entrance.
    for i in range(5):
        s.set(21+i, 7, 1, "minecraft:red_terracotta")
        if i < 3: s.set(23+i, 8, 1, "minecraft:blackstone")

    # Inner ruined courtyard wall stays outside the temporary +/-13 seal ring.
    for x in range(7, 40):
        if not (20 <= x <= 26):
            for y in range(1, 4 + ((x * 3) % 2)):
                s.set(x, y, 7, ruin_block(x, 7, y))
        if not (22 <= x <= 24):
            for y in range(1, 4 + ((x * 5) % 2)):
                s.set(x, y, 39, ruin_block(x, 39, y))
    for z in range(8,39):
        for y in range(1,4):
            s.set(7,y,z,ruin_block(7,z,y))
            s.set(39,y,z,ruin_block(39,z,y))

    # Central ritual dais offset north of exact combat origin, safely inside seal ring.
    s.fill(21, 1, 18, 25, 1, 20, "minecraft:polished_blackstone_bricks")
    s.fill(22, 2, 19, 24, 2, 19, "minecraft:chiseled_polished_blackstone")
    s.set(23, 3, 19, "minecraft:soul_lantern", hanging="false", waterlogged="false")

    # Readable rubble clusters, never on the +/-13 seal channel (local x/z 10 or 36).
    rubble = [(12,12),(15,34),(33,14),(31,32),(12,28),(34,26)]
    for x,z in rubble:
        if x in (10,36) or z in (10,36): continue
        s.set(x,1,z,"minecraft:cobblestone")
        if (x+z)%2==0: s.set(x+1,1,z,"minecraft:mossy_cobblestone")

    # Two modest loot rooms plus approach spawners.
    s.chest(5,1,12,"fallen_knight:chests/castle",facing="east")
    s.chest(41,1,34,"fallen_knight:chests/castle",facing="west")
    s.spawner(13,1,4,"minecraft:zombie")
    s.spawner(33,1,4,"minecraft:skeleton")
    s.spawner(23,1,5,"minecraft:pillager")

    # Direct seed marker is robust during worldgen: it registers once when the chunk ticks.
    s.set(23,0,23,"minecraft:lodestone")
    s.set(24,0,23,"minecraft:red_terracotta")
    s.set(22,0,23,"minecraft:blackstone")
    s.entity(23.5, 1.0, 23.5, "minecraft:marker", None)
    return s


def build_camp() -> Structure:
    s = Structure((13, 7, 13))
    for x in range(2,11):
        for z in range(2,11):
            if (x+z)%4 != 0: s.set(x,0,z,"minecraft:coarse_dirt")
    # Broken stone corner and fallen heraldic tent.
    for y in range(1,4):
        for x,z in ((1,1),(1,2),(2,1),(10,10),(10,11),(11,10)):
            if (x+y+z)%4: s.set(x,y,z,ruin_block(x,z,y))
    s.fill(4,1,4,4,4,4,"minecraft:dark_oak_log",axis="y")
    s.fill(8,1,4,8,4,4,"minecraft:dark_oak_log",axis="y")
    s.fill(4,4,4,8,4,4,"minecraft:dark_oak_log",axis="x")
    for x in range(5,8): s.set(x,3,4,"minecraft:red_wool")
    s.set(6,1,7,"minecraft:campfire",facing="north",lit="true",signal_fire="false",waterlogged="false")
    s.chest(9,1,8,"fallen_knight:chests/clue",facing="west")
    return s


def build_watchtower() -> Structure:
    s = Structure((11, 12, 11))
    s.fill(1,0,1,9,0,9,"minecraft:cobblestone")
    for y in range(1,10):
        for x in range(1,10):
            if (x+y)%7: s.set(x,y,1,ruin_block(x,1,y))
            if (x+y)%9: s.set(x,y,9,ruin_block(x,9,y))
        for z in range(2,9):
            if (z+y)%8: s.set(1,y,z,ruin_block(1,z,y))
            if (z+y)%6: s.set(9,y,z,ruin_block(9,z,y))
    # doorway and broken viewing slots
    for y in range(1,4):
        for x in range(4,7): s.blocks.pop((x,y,1),None)
    for y in (4,7):
        for x,z in ((5,1),(1,5),(9,5),(5,9)): s.blocks.pop((x,y,z),None)
    s.fill(2,8,2,8,8,8,"minecraft:dark_oak_planks")
    for x,z in ((2,2),(8,2),(2,8),(8,8)):
        s.fill(x,1,z,x,10,z,"minecraft:dark_oak_log",axis="y")
    s.set(5,9,1,"minecraft:red_wool")
    s.set(5,10,1,"minecraft:black_wool")
    s.chest(3,1,7,"fallen_knight:chests/clue",facing="south")
    return s


def main() -> None:
    build_castle().write(OUT / "castle/main.nbt")
    build_camp().write(OUT / "clue/camp.nbt")
    build_watchtower().write(OUT / "clue/watchtower.nbt")
    for p in (OUT / "castle/main.nbt", OUT / "clue/camp.nbt", OUT / "clue/watchtower.nbt"):
        print(p, p.stat().st_size)


if __name__ == "__main__":
    main()
