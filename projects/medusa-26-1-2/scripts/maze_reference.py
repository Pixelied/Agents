from __future__ import annotations

from collections import deque
from dataclasses import dataclass
import random
from typing import Iterable

ROWS = 13
COLS = 13
MIN_ROUTE = 24
MIN_DELTA = 16
MAX_DELTA = 28
ENTRANCE = (0, 0)
SANCTUM = (ROWS - 1, COLS - 1)

Cell = tuple[int, int]
Edge = tuple[Cell, Cell]


def _edge(a: Cell, b: Cell) -> Edge:
    return (a, b) if a <= b else (b, a)


def _neighbors(cell: Cell) -> Iterable[Cell]:
    row, col = cell
    if row > 0:
        yield row - 1, col
    if col + 1 < COLS:
        yield row, col + 1
    if row + 1 < ROWS:
        yield row + 1, col
    if col > 0:
        yield row, col - 1


def _all_grid_edges() -> tuple[Edge, ...]:
    edges: list[Edge] = []
    for row in range(ROWS):
        for col in range(COLS):
            if col + 1 < COLS:
                edges.append(_edge((row, col), (row, col + 1)))
            if row + 1 < ROWS:
                edges.append(_edge((row, col), (row + 1, col)))
    return tuple(edges)


ALL_GRID_EDGES = _all_grid_edges()


@dataclass(frozen=True)
class Topology:
    edges: frozenset[Edge]

    def signature(self) -> tuple[Edge, ...]:
        return tuple(sorted(self.edges))


@dataclass(frozen=True)
class Validation:
    reachable_count: int
    sanctum_distance: int

    @property
    def valid(self) -> bool:
        return self.reachable_count == ROWS * COLS and self.sanctum_distance >= MIN_ROUTE


def validate(topology: Topology) -> Validation:
    adjacency: dict[Cell, list[Cell]] = {
        (row, col): [] for row in range(ROWS) for col in range(COLS)
    }
    for a, b in topology.edges:
        adjacency[a].append(b)
        adjacency[b].append(a)

    distances: dict[Cell, int] = {ENTRANCE: 0}
    queue: deque[Cell] = deque([ENTRANCE])
    while queue:
        cell = queue.popleft()
        for neighbor in adjacency[cell]:
            if neighbor in distances:
                continue
            distances[neighbor] = distances[cell] + 1
            queue.append(neighbor)

    return Validation(
        reachable_count=len(distances),
        sanctum_distance=distances.get(SANCTUM, -1),
    )


def generate_initial(seed: int) -> Topology:
    rng = random.Random(seed)
    visited = {ENTRANCE}
    stack = [ENTRANCE]
    edges: set[Edge] = set()

    while stack:
        current = stack[-1]
        choices = [cell for cell in _neighbors(current) if cell not in visited]
        if not choices:
            stack.pop()
            continue
        nxt = rng.choice(choices)
        edges.add(_edge(current, nxt))
        visited.add(nxt)
        stack.append(nxt)

    closed = [edge for edge in ALL_GRID_EDGES if edge not in edges]
    rng.shuffle(closed)
    loop_count = rng.randint(18, 30)
    edges.update(closed[:loop_count])

    topology = Topology(frozenset(edges))
    check = validate(topology)
    if not check.valid:
        raise AssertionError(f"generated invalid topology for seed {seed}: {check}")
    return topology


def changed_edges(a: Topology, b: Topology) -> frozenset[Edge]:
    return a.edges.symmetric_difference(b.edges)


def _connected(edges: set[Edge]) -> bool:
    return validate(Topology(frozenset(edges))).reachable_count == ROWS * COLS


def propose_shift(current: Topology, seed: int, max_attempts: int = 64) -> Topology:
    if max_attempts <= 0:
        return current

    original = set(current.edges)
    original_closed = [edge for edge in ALL_GRID_EDGES if edge not in original]
    original_open = list(original)
    rng = random.Random(seed)

    # Pair one new opening with one safe closure. This is a randomized
    # edge-exchange on the current graph: every pair materially changes the
    # maze while preserving a connected spanning graph.
    for _ in range(max_attempts):
        target_delta = rng.choice(tuple(range(MIN_DELTA, MAX_DELTA + 1, 2)))
        swaps = target_delta // 2
        edges = set(original)
        used_added: set[Edge] = set()
        used_removed: set[Edge] = set()
        success = True

        add_pool = original_closed[:]
        rng.shuffle(add_pool)

        for _swap in range(swaps):
            add_candidates = [edge for edge in add_pool if edge not in used_added]
            if not add_candidates:
                success = False
                break

            added = rng.choice(add_candidates)
            edges.add(added)
            used_added.add(added)

            remove_pool = [
                edge
                for edge in original_open
                if edge not in used_removed and edge in edges
            ]
            rng.shuffle(remove_pool)
            removed = None
            for candidate in remove_pool:
                edges.remove(candidate)
                if _connected(edges):
                    removed = candidate
                    break
                edges.add(candidate)

            if removed is None:
                edges.remove(added)
                used_added.remove(added)
                success = False
                break
            used_removed.add(removed)

        if not success:
            continue

        candidate = Topology(frozenset(edges))
        delta = len(changed_edges(current, candidate))
        check = validate(candidate)
        if check.valid and MIN_DELTA <= delta <= MAX_DELTA:
            return candidate

    return current
