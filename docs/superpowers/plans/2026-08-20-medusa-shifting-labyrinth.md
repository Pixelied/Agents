# Medusa Shifting Labyrinth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three mandatory puzzle rooms with a fully roofed ~95x95 procedural Gorgon labyrinth whose topology changes about every 30 seconds through validated random mutations and visibly moving, collision-safe wall panels.

**Architecture:** Keep the existing Medusa instance anchor, arena, Eye, boss, rematch, Staff, rewards, and `md_eid` isolation contracts. The labyrinth becomes a 13x13 logical cell graph at 7-block pitch; per-instance cell markers own current/proposed connectivity, generation/validation is time-sliced, and live shifts always open new passages before closing old ones. Stable walls are real blocks; temporary `block_display` panels provide smooth motion while stepped barrier collision follows the motion, then the destination is committed back to real blocks.

**Tech Stack:** Minecraft Java 26.1.2; Java 25; vanilla datapack commands, scoreboards, markers, `/random`, macros/storage, `block_display`, barriers and scheduled functions; Python 3 standard-library topology oracle/generator tests; GitHub Actions exact-26.1.2 dedicated-server smoke tests.

**Spec:** `docs/superpowers/specs/2026-08-19-medusa-shifting-labyrinth-design.md`

## Global Constraints

- Target exactly Minecraft Java **26.1.2** and Java **25**.
- The maze footprint is approximately **90-100 blocks across**; implementation target is a **13x13 cell graph on 7-block pitch**.
- Fixed entrance and fixed sanctum destination; no moving exit.
- No finite/preset sequence of whole-maze states. Initial topology and later mutations are generated at runtime.
- Normal accepted shifts change roughly **16-28 unique passage edges** and occur about every **30 seconds** while eligible players are inside an uncleared maze.
- Every committed topology keeps entrance -> sanctum reachable and keeps all logical cells in one connected traversable graph.
- Candidate generation and flood-fill validation are bounded and time-sliced. On failure, keep the previous committed valid topology.
- Visible wall motion must use interpolated display transforms; no visible destination teleport.
- Displays are not trusted for collision. Authoritative collision follows the moving slab in discrete real/barrier-block slices.
- Transition order is always **open changed-openings first, then close changed-closures**.
- Generic moving walls never silently suffocate a player. Occupied closures pause and eventually abort/reopen.
- Spectators do not start, advance, block, complete, or scale the maze.
- All maze cells, helpers, traps, animation state, and selectors are scoped by the temple's existing `md_eid`.
- Every intended maze interior has a real authored roof; large flat developer lids are not acceptable presentation.
- Three old required puzzles and their progression flags are removed from the critical path.
- First maze completion persists through the existing `md_dungeon_clear` state. Rematches never require maze replay.
- The existing Medusa boss, Golden Gorgon Eye identity/recovery, Staff, rewards, arena coordinates, and boss lifecycle stay compatible.

## Canonical Maze Runtime State

Add and use these names consistently:

- `md_mrow`, `md_mcol` - logical cell row/column, 0..12.
- `md_mn`, `md_me`, `md_ms`, `md_mw` - committed open-edge state for north/east/south/west, 0/1.
- `md_nn`, `md_ne`, `md_ns`, `md_nw` - proposed NEXT open-edge state, 0/1.
- `md_mseen` - validation flood-fill visited flag.
- `md_mfront` - validation frontier flag.
- `md_mdist` - BFS/flood distance from entrance.
- `md_mparent` - initial randomized-DFS parent direction: 0 none, 1 north, 2 east, 3 south, 4 west.
- `md_mphase` - instance maze state: 0 idle/uninitialized, 1 initial-generation, 2 stable, 3 proposing, 4 validating, 5 warning, 6 opening, 7 closing, 8 committing, 9 frozen/cleared.
- `md_mtick` - cadence/animation timer.
- `md_mtry` - bounded proposal attempt count.
- `md_mdelta` - unique changed-edge count for accepted proposal.
- `md_mblocked` - wall closure blocked-by-player grace timer.
- Cell markers: `minecraft:marker`, tags `md.maze.cell` and `md.maze.new_cell`, with copied `md_eid`.
- Temporary wall displays: tag `md.maze.wall_display`, copied `md_eid`.
- Trap controllers/helpers: `md.maze.trap`, copied `md_eid`.

A connection is mirrored: if cell `(r,c)` has east open, `(r,c+1)` must have west open. Runtime tests must reject asymmetry.

## File Structure

- `projects/medusa-26-1-2/scripts/maze_reference.py` - pure-Python graph model/test oracle for initial generation and shifts.
- `projects/medusa-26-1-2/scripts/generate_temple.py` - static architecture generator only; no fixed maze topology.
- `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/` - all runtime graph/generation/validation/shift/completion logic.
- `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/` - stable wall, display animation, collision, blocked-closure handling.
- `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/` - bounded authored trap framework.
- `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/maze/` - static shell, floor, roofs, landmarks and containment.
- `projects/medusa-26-1-2/tests/test_maze_reference.py` - randomized host-side algorithm tests.
- `projects/medusa-26-1-2/tests/test_dungeon_contract.py` - architecture and removed-puzzle contracts.
- `projects/medusa-26-1-2/tests/test_dungeon_runtime_contract.py` - exact-runtime marker/ordering contract.
- `.github/workflows/medusa-26-1-2-ci.yml` - exact 26.1.2 runtime proof.

---

### Task 1: Replace the old puzzle contract with a procedural-maze reference model

**Files:**
- Create: `projects/medusa-26-1-2/scripts/maze_reference.py`
- Create: `projects/medusa-26-1-2/tests/test_maze_reference.py`
- Modify: `projects/medusa-26-1-2/tests/test_dungeon_contract.py`
- Delete later in Task 8: `projects/medusa-26-1-2/tests/test_puzzle_ui_contract.py`

**Interfaces:**
- Produces Python functions `generate_initial(seed)`, `propose_shift(current, seed)`, `validate(topology)`, `changed_edges(a,b)`, and constants `ROWS=13`, `COLS=13`, `MIN_ROUTE=24`, `MIN_DELTA=16`, `MAX_DELTA=28`.
- This is the correctness oracle for the datapack algorithm; it is not runtime gameplay.

- [ ] **Step 1: Write failing algorithm tests**

```python
# tests/test_maze_reference.py
import unittest
from scripts.maze_reference import (
    ROWS, COLS, MIN_DELTA, MIN_ROUTE,
    changed_edges, generate_initial, propose_shift, validate,
)

class MazeReferenceContract(unittest.TestCase):
    def test_initial_and_many_shifts_are_connected_and_nontrivial(self):
        for seed in range(100):
            current = generate_initial(seed)
            check = validate(current)
            self.assertEqual(check.reachable_count, ROWS * COLS)
            self.assertGreaterEqual(check.sanctum_distance, MIN_ROUTE)
            for cycle in range(20):
                nxt = propose_shift(current, seed * 1000 + cycle)
                check = validate(nxt)
                self.assertEqual(check.reachable_count, ROWS * COLS)
                self.assertGreaterEqual(check.sanctum_distance, MIN_ROUTE)
                self.assertGreaterEqual(len(changed_edges(current, nxt)), MIN_DELTA)
                current = nxt
```

- [ ] **Step 2: Run and verify RED**

```bash
cd projects/medusa-26-1-2
python3 -m unittest tests.test_maze_reference -v
```

Expected: import/module failure because `maze_reference.py` does not exist.

- [ ] **Step 3: Implement the pure-Python graph model**

Use undirected edges represented canonically as `((r1,c1),(r2,c2))` with sorted endpoints. `generate_initial(seed)` uses randomized DFS from entrance `(0,0)`, then opens 18-30 additional valid internal edges for loops. `validate()` performs BFS from `(0,0)` and returns `reachable_count` plus shortest distance to sanctum `(12,12)`. `propose_shift()` starts from CURRENT, opens a random set of closed edges, proposes random closures, rejects disconnected/short candidates, requires 16-28 changed unique edges, and after a hard 64-candidate budget returns CURRENT unchanged rather than an invalid graph.

- [ ] **Step 4: Add explicit fallback and diversity tests**

```python
def test_bounded_failure_keeps_previous_valid_state(self):
    current = generate_initial(7)
    nxt = propose_shift(current, 99, max_attempts=0)
    self.assertEqual(nxt, current)

def test_seeds_do_not_collapse_to_a_small_preset_set(self):
    signatures = {generate_initial(seed).signature() for seed in range(64)}
    self.assertGreater(len(signatures), 56)
```

Run:

```bash
python3 -m unittest tests.test_maze_reference -v
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/medusa-26-1-2/scripts/maze_reference.py projects/medusa-26-1-2/tests/test_maze_reference.py projects/medusa-26-1-2/tests/test_dungeon_contract.py
git commit -m "test: define procedural Medusa maze contract"
```

### Task 2: Rebuild static labyrinth architecture as a roofed 13x13 cell shell

**Files:**
- Modify: `projects/medusa-26-1-2/scripts/generate_temple.py`
- Modify generated: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/build_generated.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/maze/build_shell.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/maze/build_roofs.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/maze/build_landmarks.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/maze/build_containment.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/build_descent.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/build_sanctum.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_dungeon_contract.py`

**Interfaces:**
- Static cell centers are generated at 7-block pitch with entrance `(0,0)` adjoining the descent and sanctum `(12,12)` adjoining `build_sanctum`.
- Dynamic passages are 3 blocks wide x 7 blocks tall inside the walls between adjacent cells.

- [ ] **Step 1: Replace fixed-maze tests with failing large-roofed-shell tests**

```python
def test_shifting_labyrinth_geometry_contract(self):
    text = (ROOT / "scripts/generate_temple.py").read_text()
    self.assertIn("MAZE_ROWS = 13", text)
    self.assertIn("MAZE_COLS = 13", text)
    self.assertIn("CELL_PITCH = 7", text)
    generated = OUT.read_text()
    for fn in ["build_shell", "build_roofs", "build_landmarks", "build_containment"]:
        self.assertIn(f"function medusa:dungeon/maze/{fn}", generated)
    self.assertNotIn("build_puzzle_averted_room", generated)
    self.assertNotIn("build_puzzle_borrowed_room", generated)
    self.assertNotIn("build_puzzle_blind_room", generated)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_dungeon_contract -v
```

Expected: old generator still declares `CINEMATIC_MAZE_V2` and puzzle rooms.

- [ ] **Step 3: Make `generate_temple.py` generate architecture, not topology**

Use constants:

```python
MAZE_ROWS = 13
MAZE_COLS = 13
CELL_PITCH = 7
MAZE_BASE_X = -44
MAZE_BASE_Y = -18
MAZE_BASE_Z = 30
PASSAGE_WIDTH = 3
WALL_HEIGHT = 8
```

The generator emits the outer shell/floor/cell chambers and fixed wall bands, but does not decide which internal edges are open. Internal edge portals start closed so runtime initialization can materialize the generated topology. Update the descent to connect directly to the `(0,0)` entrance chamber and the far `(12,12)` chamber to connect physically into the existing sanctum.

- [ ] **Step 4: Build authored roofs and visual districts**

`build_roofs.mcfunction` must use repeated rib/barrel-vault modules, stairs/slabs/walls, ceiling ribs, dark retraction cavities above mutable portals, hanging chains/lanterns, and occasional taller 13-16-block landmark chambers. `build_landmarks.mcfunction` defines at least six visually distinct districts: serpent-column gallery, moss/root crypt, lava-cracked district, petrified expedition, tall central junction, and Gorgon-relief corridor. `build_containment.mcfunction` adds hidden side/roof anti-cheese containment without replacing visible roof architecture.

- [ ] **Step 5: Regenerate and prove deterministic static architecture**

```bash
python3 scripts/generate_temple.py
python3 scripts/generate_temple.py --check
python3 -m unittest tests.test_dungeon_contract -v
python3 scripts/validate_medusa.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add projects/medusa-26-1-2/scripts/generate_temple.py projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon projects/medusa-26-1-2/tests/test_dungeon_contract.py
git commit -m "feat: build roofed shifting labyrinth shell"
```

### Task 3: Add instance-scoped cell markers and runtime randomized initial DFS

**Files:**
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/load.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/register.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/tick_one.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/setup/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/setup/spawn_cells.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/setup/spawn_cell.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/generate/tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/generate/step.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/generate/try_direction.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/generate/backtrack.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/generate/add_loops.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_instance_contract.py`

**Interfaces:**
- Each temple owns exactly 169 `md.maze.cell` markers with copied `md_eid`, `md_mrow`, `md_mcol`.
- Runtime DFS creates a connected initial topology using `/random` and `md_mparent`; no pre-generated layout list.

- [ ] **Step 1: Write failing instance-isolation/state tests**

```python
def test_maze_cells_are_instance_scoped_and_runtime_generated(self):
    load = (FN / "load.mcfunction").read_text()
    for obj in ["md_mrow", "md_mcol", "md_mn", "md_me", "md_ms", "md_mw", "md_mphase", "md_mtick"]:
        self.assertIn(f"scoreboard objectives add {obj} dummy", load)
    setup = (FN / "maze/setup/spawn_cell.mcfunction").read_text()
    self.assertIn("md.maze.cell", setup)
    self.assertIn("md_eid", setup)
    self.assertIn("/random", (FN / "maze/generate/try_direction.mcfunction").read_text())
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_instance_contract -v
```

- [ ] **Step 3: Add objectives and spawn the logical grid**

`instance/register` initializes `md_mphase=0`, builds static geometry, then calls `maze/setup/start`. Spawn 13x13 markers at logical cell centers. Each marker inherits the anchor `md_eid`; row/column are fixed scores. Boundary directions are permanently closed. Interior current/next edges begin 0.

- [ ] **Step 4: Implement time-sliced randomized DFS**

Entrance marker becomes visited/current cursor. Each generation step uses `/random value 1..4` to choose a direction, retries only unvisited in-bounds neighbors, opens the mirrored edge, records the child's parent direction and advances cursor. If no unvisited neighbor remains, `backtrack` follows `md_mparent` to the previous cell. Process a bounded small number of DFS operations per tick. When all 169 cells are visited, open 18-30 additional random closed edges to create loops and set anchor `md_mphase=2`.

- [ ] **Step 5: Add symmetry and count tests**

The static test must scan functions for mirrored writes; the exact-runtime test added later will count 169 cells and verify a runtime BFS reaches all 169.

Run:

```bash
python3 -m unittest tests.test_instance_contract -v
python3 scripts/validate_medusa.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/load.mcfunction projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze projects/medusa-26-1-2/tests/test_instance_contract.py
git commit -m "feat: generate per-instance Medusa maze topology"
```

### Task 4: Implement randomized NEXT mutations and time-sliced connectivity validation

**Files:**
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/propose/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/propose/copy_current.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/propose/mutate.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/propose/count_delta.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/validate/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/validate/tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/validate/spread.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/validate/accept.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/validate/reject.mcfunction`
- Create: `projects/medusa-26-1-2/tests/test_maze_datapack_contract.py`

**Interfaces:**
- `propose/start` derives NEXT from CURRENT, never edits visible blocks.
- `validate/accept` is the only path from proposed state into warning/animation.
- Hard proposal budget: 64 attempts; failure returns to stable CURRENT.

- [ ] **Step 1: Write failing proposal/validation contracts**

```python
class MazeDatapackContract(unittest.TestCase):
    def test_proposal_has_bounded_retry_and_delta_gate(self):
        propose = (FN / "maze/propose/start.mcfunction").read_text()
        validate = (FN / "maze/validate/accept.mcfunction").read_text()
        self.assertIn("md_mtry", propose)
        self.assertIn("16", validate)
        self.assertIn("28", validate)

    def test_validation_is_time_sliced(self):
        tick = (FN / "maze/validate/tick.mcfunction").read_text()
        self.assertIn("function medusa:maze/validate/spread", tick)
        self.assertNotIn("schedule function medusa:maze/validate/tick 0t", tick)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_maze_datapack_contract -v
```

- [ ] **Step 3: Implement candidate mutation**

Copy current directional scores into next scores. Use `/random` to open and close randomly selected unique internal edges while maintaining mirrored NEXT values. Target 16-28 changed unique edges, with both openings and closures unless the candidate budget requires a more-open fallback. No block commands run here.

- [ ] **Step 4: Implement BFS/flood validation**

Clear `md_mseen/md_mfront/md_mdist`; mark entrance front=1, distance=0. Each tick expands a bounded number of front cells across NEXT open edges, marking unseen neighbors and distance+1. Accept only when all 169 cells are seen, sanctum is seen with distance >=24, and `md_mdelta` is 16..28. Reject resets NEXT to CURRENT, increments `md_mtry`, and either retries or returns stable after attempt 64.

- [ ] **Step 5: Run the reference and datapack contracts**

```bash
python3 -m unittest tests.test_maze_reference tests.test_maze_datapack_contract -v
python3 scripts/validate_medusa.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze projects/medusa-26-1-2/tests/test_maze_datapack_contract.py
git commit -m "feat: validate randomized maze shifts"
```

### Task 5: Add 30-second scheduler, warning sequence, and open-first/close-second transition

**Files:**
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/activity/check_players.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/warning/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/warning/tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/transition/start_open.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/transition/open_tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/transition/start_close.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/transition/close_tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/transition/commit.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/tick_one.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_maze_datapack_contract.py`

**Interfaces:**
- Stable timer counts only while a non-Spectator Survival/Adventure player is inside the uncleared maze.
- Transition phase ordering is observable in runtime markers/tests.

- [ ] **Step 1: Write failing ordering/activity tests**

```python
def test_shift_opens_before_it_closes(self):
    start_open = (FN / "maze/transition/start_open.mcfunction").read_text()
    open_tick = (FN / "maze/transition/open_tick.mcfunction").read_text()
    self.assertIn("start_close", open_tick)
    self.assertNotIn("start_close", start_open)

def test_spectators_do_not_drive_maze_activity(self):
    text = (FN / "maze/activity/check_players.mcfunction").read_text()
    self.assertIn("gamemode=!spectator", text)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_maze_datapack_contract -v
```

- [ ] **Step 3: Implement cadence**

Stable exploration runs approximately 23 seconds before proposal/validation begins. Once valid NEXT exists, warning runs for about 60 ticks: distant deepslate/stone grind, increasingly local scraping, falling-dust particles at changed wall tracks, and a final low impact cue. If no eligible player is inside, timer/proposal work pauses.

- [ ] **Step 4: Enforce transition ordering**

`start_open` finds edges CURRENT=closed/NEXT=open and starts their wall-opening animation. Only after every opening reports complete does `start_close` process CURRENT=open/NEXT=closed edges. `commit` copies the actually achieved NEXT directional state into CURRENT, resets temporary NEXT/validation state, sets `md_mphase=2`, and restarts cadence.

- [ ] **Step 5: Test and commit**

```bash
python3 -m unittest tests.test_maze_datapack_contract -v
python3 scripts/validate_medusa.py
git add projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/tick_one.mcfunction projects/medusa-26-1-2/tests/test_maze_datapack_contract.py
git commit -m "feat: orchestrate aggressive maze shifts"
```

### Task 6: Implement visibly moving wall panels with stepped authoritative collision

**Files:**
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/open_start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/open_tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/close_start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/close_tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/spawn_display.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/check_occupied.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/abort_close.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/finish.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall/cleanup.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_maze_datapack_contract.py`

**Interfaces:**
- Default mutable panel is a 3-wide x 7-high slab retracting upward into the roof cavity over about 60-80 ticks.
- Temporary helpers carry `md_eid`; stable closed state ends as detailed real wall blocks.

- [ ] **Step 1: Write failing interpolation/collision tests**

```python
def test_moving_walls_use_display_interpolation_and_real_collision(self):
    spawn = (FN / "maze/wall/spawn_display.mcfunction").read_text()
    self.assertIn("minecraft:block_display", spawn)
    self.assertIn("interpolation_duration", spawn)
    self.assertIn("transformation", spawn)
    close = (FN / "maze/wall/close_tick.mcfunction").read_text()
    self.assertIn("minecraft:barrier", close)
    self.assertIn("check_occupied", close)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_maze_datapack_contract -v
```

- [ ] **Step 3: Implement opening animation**

Same tick: replace the stable wall face with a temporary display at identical visible position and establish matching barrier collision. Animate display translation upward using complete 26.1.2 transformation maps and `interpolation_duration`. Remove barrier rows bottom-up as the display clears them. When fully open, remove all display/barrier helpers and leave passage air.

- [ ] **Step 4: Implement safe closing animation**

Spawn the display inside the roof recess and interpolate downward. Introduce barrier collision slices as the slab enters the passage. Before advancing each slice, check eligible players in that sweep volume. A blocked closure waits up to 40 ticks; if still occupied, `abort_close` animates/retracts the panel back open, forces that NEXT edge open symmetrically, and lets the rest of the shift continue. Generic walls do not apply damage.

- [ ] **Step 5: Add cleanup guarantees and transformation guard**

`wall/cleanup` kills only `md.maze.wall_display`/wall helper entities matching the instance `md_eid` and clears leftover barrier volume for incomplete edges before restoring committed state. Extend the existing display-transformation test so every new wall display has translation, left_rotation, scale and right_rotation.

Run:

```bash
python3 -m unittest tests.test_maze_datapack_contract tests.test_runtime_contract -v
python3 scripts/validate_medusa.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/wall projects/medusa-26-1-2/tests
git commit -m "feat: animate collision-safe shifting walls"
```

### Task 7: Add bounded trap framework and seven authored hazard families

**Files:**
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/tick.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/rearm.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/cleanup.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/serpent_nest/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/venom_gallery/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/lava_fissure/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/crusher/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/gorgon_relief/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/drop_route/start.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap/expedition/start.mcfunction`
- Create: `projects/medusa-26-1-2/tests/test_maze_trap_contract.py`

**Interfaces:**
- Trap controllers are attached only to authored eligible landmark cells and carry `md_eid`.
- At most 4 trap modules may be armed/active per instance at once; trap selection can reroll after a completed shift.

- [ ] **Step 1: Write failing trap safety tests**

```python
class MazeTrapContract(unittest.TestCase):
    def test_all_required_trap_families_exist(self):
        for rel in ["serpent_nest", "venom_gallery", "lava_fissure", "crusher", "gorgon_relief", "drop_route", "expedition"]:
            self.assertTrue((FN / f"maze/trap/{rel}/start.mcfunction").is_file())

    def test_traps_are_instance_scoped_and_bounded(self):
        text = (FN / "maze/trap/rearm.mcfunction").read_text()
        self.assertIn("md_eid", text)
        self.assertIn("4", text)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_maze_trap_contract -v
```

- [ ] **Step 3: Implement trap controller/rearm logic**

After a stable shift, choose up to four eligible trap anchors whose cells are reachable and whose hazard footprint does not consume every open edge from the cell. Telegraph before activation; scope every helper by `md_eid`; remove helpers on completion/reset.

- [ ] **Step 4: Implement authored hazards**

Serpent Nest releases 2-4 fragile tagged snakes; Venom Gallery uses visible wall slits/particles before projectile lanes; Lava Fissure uses a fixed architectural trench with a safe traversable side/stepping route; Crusher is a separately telegraphed damaging mechanism, not generic wall safety; Gorgon Relief uses glowing wall eyes and the existing gaze/LOS style; Drop Route sends players to a lower bypass and reconnects; Expedition reveals environmental storytelling plus a short ambush. None may permanently make the logical graph unsolvable.

- [ ] **Step 5: Test and commit**

```bash
python3 -m unittest tests.test_maze_trap_contract -v
python3 scripts/validate_medusa.py
git add projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/trap projects/medusa-26-1-2/tests/test_maze_trap_contract.py
git commit -m "feat: add shifting labyrinth traps"
```

### Task 8: Remove the three puzzle systems and gate progression on legitimate sanctum completion

**Files:**
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/puzzle/averted_eyes/`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/puzzle/borrowed_gaze/`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/puzzle/blind_passage/`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/puzzle/tick_all.mcfunction`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/puzzle/complete.mcfunction`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/build_puzzle_averted_room.mcfunction`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/build_puzzle_borrowed_room.mcfunction`
- Delete: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/dungeon/build_puzzle_blind_room.mcfunction`
- Delete: `projects/medusa-26-1-2/tests/test_puzzle_ui_contract.py`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/load.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/register.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/tick_one.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/completion/check.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/completion/complete.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/arena/pedestal/locked_feedback.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_dungeon_contract.py`
- Modify: `projects/medusa-26-1-2/tests/test_pedestal_ui_contract.py`

**Interfaces:**
- Preserve `md_dungeon_clear` as the canonical persistent first-clear flag so pedestal/boss/rematch integration stays stable.
- Completion only counts an eligible non-Spectator crossing the sanctum threshold from the labyrinth side.

- [ ] **Step 1: Write failing removed-puzzle/progression tests**

```python
def test_old_puzzles_are_not_on_the_critical_path(self):
    tick = (FN / "instance/tick_one.mcfunction").read_text()
    self.assertNotIn("medusa:puzzle/", tick)
    load = (FN / "load.mcfunction").read_text()
    for obj in ["md_p1_done", "md_p2_done", "md_p3_done", "md_p1_o1", "md_p2_left", "md_p3_zone"]:
        self.assertNotIn(obj, load)

def test_locked_eye_feedback_points_to_labyrinth(self):
    text = (FN / "arena/pedestal/locked_feedback.mcfunction").read_text().lower()
    self.assertIn("labyrinth", text)
    self.assertNotIn("three trials", text)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_dungeon_contract tests.test_pedestal_ui_contract -v
```

- [ ] **Step 3: Implement completion/freeze**

`maze/completion/check` runs only in uncleared state and ignores Spectators. Crossing the sanctum-side threshold calls `complete`: set anchor `md_dungeon_clear=1`, `md_mphase=9`, stop shifts/traps, ensure the currently committed topology is valid, freeze/open a safe path for teammates, and permanently open the sanctum->arena approach. The Golden Eye stays locked until this flag is set; existing pedestal resolve logic can continue using `md_dungeon_clear`.

- [ ] **Step 4: Remove obsolete code/objectives and update text**

Delete all puzzle functions/rooms/tests and remove `md_p1_*`, `md_p2_*`, `md_p3_*` objectives/initialization. Update comments/docs from `surface -> puzzles -> ...` to `surface -> descent -> shifting labyrinth -> sanctum -> arena`.

- [ ] **Step 5: Run full static suite and commit**

```bash
python3 -m unittest discover -s tests -v
python3 scripts/validate_medusa.py
git add -A projects/medusa-26-1-2
git commit -m "refactor: replace Medusa puzzles with shifting maze"
```

### Task 9: Add maze cleanup and restart recovery from an interrupted shift

**Files:**
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/recovery/rebuild_committed.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/recovery/cleanup_transient.mcfunction`
- Create: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/recovery/recover_one.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance/recover_one.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/arena/reset/cleanup_scoped.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_instance_contract.py`

**Interfaces:**
- Committed CURRENT cell scores are source of truth after a restart; temporary displays/barriers/NEXT state are disposable.

- [ ] **Step 1: Write failing recovery cleanup tests**

```python
def test_maze_recovery_is_instance_scoped(self):
    cleanup = (FN / "maze/recovery/cleanup_transient.mcfunction").read_text()
    self.assertIn("md.maze.wall_display", cleanup)
    self.assertIn("md_eid=$(eid)", cleanup)
    recover = (FN / "instance/recover_one.mcfunction").read_text()
    self.assertIn("medusa:maze/recovery/recover_one", recover)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_instance_contract -v
```

- [ ] **Step 3: Implement deterministic recovery**

On load, remove matching instance temporary displays/trap helpers and clear transient barrier slices. Rebuild every internal passage from committed CURRENT directional scores. Discard uncommitted NEXT. If maze was uncleared, return to stable `md_mphase=2`; if `md_dungeon_clear=1`, return to frozen `md_mphase=9`. Never infer topology from leftover world blocks.

- [ ] **Step 4: Integrate reset cleanup**

Arena/reset cleanup must kill/restore only maze helpers with the same `md_eid`; do not delete permanent cell markers for a still-existing temple. If a temple is fully removed in a future feature, provide a separate delete path rather than piggybacking encounter reset.

- [ ] **Step 5: Test and commit**

```bash
python3 -m unittest tests.test_instance_contract -v
python3 scripts/validate_medusa.py
git add projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/maze/recovery projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/instance projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/arena/reset projects/medusa-26-1-2/tests/test_instance_contract.py
git commit -m "fix: recover interrupted maze shifts safely"
```

### Task 10: Replace puzzle smoke tests with exact-runtime shifting-maze proof

**Files:**
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/debug/test_dungeon_progression.mcfunction`
- Replace content: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/debug/test_dungeon_progression_instance.mcfunction`
- Modify: `projects/medusa-26-1-2/datapacks/medusa/data/medusa/function/debug/create_test_temple_loaded.mcfunction`
- Modify: `projects/medusa-26-1-2/tests/test_dungeon_runtime_contract.py`
- Modify: `.github/workflows/medusa-26-1-2-ci.yml`

**Interfaces:**
- Runtime smoke must use the same direct-install datapack ZIP path delivered to users.
- New log markers replace `MEDUSA_P1_GATE_OK`, `MEDUSA_P2_GATE_OK`, `MEDUSA_P3_GATE_OK`.

- [ ] **Step 1: Write failing runtime marker contract**

```python
markers = [
    "MEDUSA_MAZE_CELLS_OK",
    "MEDUSA_MAZE_INITIAL_SOLVABLE_OK",
    "MEDUSA_MAZE_DELTA_OK",
    "MEDUSA_MAZE_OPEN_FIRST_OK",
    "MEDUSA_MAZE_WALL_DISPLAY_OK",
    "MEDUSA_MAZE_COLLISION_OK",
    "MEDUSA_MAZE_OCCUPIED_ABORT_OK",
    "MEDUSA_MAZE_INSTANCE_ISOLATION_OK",
    "MEDUSA_MAZE_RECOVERY_OK",
    "MEDUSA_MAZE_COMPLETE_OK",
]
text = (FN / "debug/test_dungeon_progression_instance.mcfunction").read_text()
for marker in markers:
    self.assertIn(marker, text)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_dungeon_runtime_contract -v
```

- [ ] **Step 3: Build deterministic debug orchestration around random runtime logic**

The debug function waits until initial generation reaches stable, counts exactly 169 cell markers, invokes validation and emits initial-solvable marker, records a topology signature/count, forces one proposal/shift using normal runtime functions, confirms >=16 unique edge changes, emits an opening marker before closing marker, observes at least one `block_display` with wall tag and valid interpolation, confirms final collision blocks match committed topology, and revalidates connectivity.

For occupied closure, place a non-player test actor in one closing sweep zone, run normal blocked-wall handling past grace, assert that edge ends open and actor remains alive. Create a second test instance far away, shift only one, and prove the other's cell scores/helpers remain unchanged. Simulate `md_mphase=6/7` with temporary wall display/barrier then run recovery and prove restoration to committed topology. Finally simulate legitimate sanctum crossing and assert `md_dungeon_clear=1`, `md_mphase=9`, Eye accessible.

- [ ] **Step 4: Update exact 26.1.2 CI assertions**

Replace old puzzle greps with the markers above. Keep all existing boss, gaze, Staff, lifecycle, Eye identity, parser/load/serialization rejection checks. Increase runtime timeout only as much as measured necessary for the larger staged temple build; do not hide an unbounded hang with a huge timeout.

- [ ] **Step 5: Run full CI-equivalent static suite locally where possible and commit**

```bash
cd projects/medusa-26-1-2
python3 -m unittest discover -s tests -v
python3 scripts/validate_medusa.py
python3 scripts/generate_temple.py --check
```

Expected: PASS before pushing; GitHub exact-runtime must then pass on Mojang's 26.1.2 server.

```bash
git add projects/medusa-26-1-2 .github/workflows/medusa-26-1-2-ci.yml
git commit -m "test: verify procedural shifting maze at runtime"
```

### Task 11: Update documentation, manual acceptance, and performance gates

**Files:**
- Modify: `projects/medusa-26-1-2/README.md`
- Modify if necessary: `projects/medusa-26-1-2/scripts/validate_medusa.py`
- Modify if necessary: `.github/workflows/medusa-26-1-2-ci.yml`

**Interfaces:**
- No gameplay change; this task makes the actual delivery/test procedure match the redesigned dungeon.

- [ ] **Step 1: Write failing README contract in `test_dungeon_contract.py`**

```python
def test_readme_describes_shifting_labyrinth_not_three_puzzles(self):
    text = (ROOT / "README.md").read_text().lower()
    self.assertIn("shifting labyrinth", text)
    self.assertIn("30 seconds", text)
    self.assertNotIn("three required gorgon puzzles", text)
```

- [ ] **Step 2: Run and verify RED**

```bash
python3 -m unittest tests.test_dungeon_contract -v
```

- [ ] **Step 3: Update install/debug/manual test documentation**

Document first-clear flow, ~95x95 scale, runtime shifts, fixed sanctum destination, wall warning/movement, traps, rematch skip, and debug commands. Manual client checklist must explicitly require: spectator roof inspection; no visible wall teleports; collision follows displays; sound/particles align; multiple shifts visibly alter multiple regions; no trapped players; trap telegraphs; 2-player and 4+ synchronized wall motion; two simultaneous temple instances; restart mid-shift; resource pack disabled fallback; and first-clear time/maze readability feedback.

- [ ] **Step 4: Add performance acceptance thresholds to CI/debug logs**

Record maze generation duration, validation attempt count, changed-edge count, and maximum simultaneously active wall displays/trap helpers. Acceptance targets: normal shift proposal/validation completes well before the next 30-second cycle; active wall displays remain bounded to the changed panels, not the whole maze; no continuous generation when no eligible player is present. Treat sustained server `Can't keep up!` during ordinary shifts as a release blocker even if functional markers pass.

- [ ] **Step 5: Final full verification**

```bash
cd projects/medusa-26-1-2
python3 -m unittest discover -s tests -v
python3 scripts/validate_medusa.py
python3 scripts/generate_temple.py --check
```

Then require a fresh GitHub Actions run where `static-contracts`, exact Mojang **26.1.2** `exact-runtime`, and `package` all conclude `success`. Download that exact run's artifact and independently verify ZIP integrity plus root `pack.mcmeta` for both direct-install packs before handing it to the user.

- [ ] **Step 6: Commit**

```bash
git add projects/medusa-26-1-2/README.md projects/medusa-26-1-2/tests/test_dungeon_contract.py projects/medusa-26-1-2/scripts/validate_medusa.py .github/workflows/medusa-26-1-2-ci.yml
git commit -m "docs: document Medusa shifting labyrinth release gates"
```

## Self-Review

- **Spec coverage:** Tasks 1/4 cover randomized topology, solvability, meaningful delta and bounded failure; Task 2 covers the ~95x95 shell, complete authored roofs and landmarks; Tasks 3/5 cover per-instance runtime generation and ~30-second aggressive cadence; Task 6 covers smooth display motion plus authoritative collision and occupied-wall safety; Task 7 covers all seven hazard families; Task 8 removes the old puzzles and preserves `md_dungeon_clear`/rematch compatibility; Task 9 covers interrupted-shift recovery; Task 10 provides exact 26.1.2 runtime evidence including two-instance isolation; Task 11 covers human visual/navigation and performance acceptance.
- **No preset states:** no task introduces a finite list of full maze layouts. Python topology code is only an oracle; runtime uses `/random` and current-state mutation.
- **No visible teleport promise:** every normal live wall change passes through the wall animation functions. Restart recovery may snap to committed state as explicitly allowed by the spec.
- **Collision accuracy:** the plan does not pretend displays are solid. Barrier/real-block slices are the collision authority, with occupied closure abort behavior.
- **Progression compatibility:** `md_dungeon_clear` remains the existing pedestal gate, so boss/Eye/rematch code does not need a new incompatible state model.
- **Instance consistency:** every persistent or temporary maze entity is required to inherit `md_eid`; tests cover cross-temple isolation.
- **Scope:** natural worldgen, boss redesign, Staff changes, reward changes and new custom wall textures are not part of this plan.
