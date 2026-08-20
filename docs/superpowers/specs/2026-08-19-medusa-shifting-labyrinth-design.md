# Medusa Shifting Labyrinth Redesign — Minecraft Java 26.1.2

**Status:** Approved concept, written spec for human review  
**Target:** Minecraft Java 26.1.2 / Java 25  
**Branch:** `fix/medusa-dungeon-rebuild`  
**Scope:** Replace the current required puzzle progression with one large procedural shifting labyrinth while preserving the approved Medusa arena, boss, rewards, rematch, Staff, and instance-isolation systems.

## 1. Superseded design

This document intentionally overrides the dungeon-progression parts of `2026-08-18-medusa-boss-26-1-2-design.md` that require a fixed handcrafted maze and the three mandatory puzzles **Averted Eyes**, **Borrowed Gaze**, and **The Blind Passage**.

The old puzzle rooms and their required progression flags are removed from the first-clear critical path. The new first-clear progression is:

**Ruined Surface Temple → Underground Descent → Procedural Shifting Labyrinth → Inner Serpent Sanctum → Medusa Arena**

The labyrinth itself is the challenge. There are no mandatory logic/optics/button puzzles inside it. Environmental hazards and traps can be complex, but they are survival/navigation obstacles rather than puzzle gates.

The existing rule that rematches do not require replaying the full dungeon remains. Once an instance has been cleared, its maze-clear state is persistent and the rematch ritual remains arena/pedestal based.

## 2. Experience goals

The labyrinth should feel like ancient Gorgon architecture that is alive and mechanically hostile, not like a normal Minecraft maze with doors toggling.

Required qualities:

- approximately **90–100 blocks across**; initial target is about **95×95**;
- fully enclosed with authored floors, tall walls, and real roofs;
- fixed physical entrance and fixed physical sanctum/exit;
- procedural runtime topology with no short repeating list of preset states;
- major route changes roughly every **30 seconds** while players are actively traversing it;
- every accepted topology is solvable from entrance to sanctum;
- every accepted topology differs materially from the previous topology;
- wall movement is visibly continuous and physical-looking, never a visible instant teleport;
- multiplayer-safe and instance-local;
- difficult and disorienting, but not dependent on pure luck or permanent traps;
- strong environmental detail and recognizable districts/landmarks so difficulty comes from changing routes rather than identical gray corridors.

The intended first-clear labyrinth time is roughly **10–20 minutes** depending on player navigation, traps, shifting timing, and group size. This is an initial tuning target, not a hard timeout.

## 3. Physical layout

The initial implementation target is a **13×13 logical cell graph** on roughly a **7-block pitch**, producing a physical footprint near 91 blocks before the outer shell and architectural margins. Final footprint may vary slightly while staying near the approved ~95×95 target.

Each logical cell is a traversable room/corridor volume. Edges between neighboring cells are mutable passage modules. A passage edge is either:

- **open** — players can travel between those cells; or
- **closed** — a tall physical wall panel blocks the connection.

The entrance cell and sanctum cell never move. The sanctum is on the far side of the labyrinth so the destination remains spatially meaningful even as routes change.

The maze is not a flat two-block-high grid. Normal corridor walls should read as roughly **8–10 blocks tall** before the ceiling architecture. Major chambers can rise to roughly **13–16 blocks**.

## 4. Roof and architectural direction

Every intended interior maze space must have a real roof. No open developer-box rooms and no giant flat stone lid as the final presentation.

Normal corridors use combinations of:

- rib-vault and barrel-vault ceiling modules;
- stone arches spanning passages;
- inset ceiling ribs aligned with maze cells;
- carved serpent motifs and structural braces;
- hanging chains and lanterns;
- roots, moss, water drips, cracked masonry, and rubble;
- dark mechanical recesses above movable wall panels;
- occasional partially collapsed sections that still remain enclosed by an outer anti-escape shell.

Large junctions and landmark rooms can use taller domed/vaulted roofs, oversized Gorgon faces, hanging stone ornaments, broken upper galleries, and roof cavities that visibly explain where moving wall slabs retract.

The palette should use layered vanilla materials rather than one repeated block: stone bricks, cracked/mossy variants, deepslate/tuff families, stairs/slabs/walls for depth, oxidized-green accents where useful, chains, lanterns/soul lighting, vines/roots, and localized decorative materials. The optional resource pack may enhance presentation, but the dungeon must still look intentional without it.

The outer sides and roof receive a hidden anti-cheese collision shell so players cannot simply mine through the maze or roof to skip the mechanic. Visible architecture remains ordinary detailed blocks; the hidden shell is gameplay containment, not the visible aesthetic.

## 5. Runtime topology model

There is **no repeating list of global maze states**.

Each temple instance owns its own topology state. The recommended representation is lightweight per-instance marker data for logical cells and mutable edges, each scoped by the existing encounter ID (`md_eid`). A mutable edge records at minimum:

- current open/closed state;
- proposed next state;
- orientation/location;
- animation state;
- wall-module style;
- instance ID.

Cell state supports path validation/frontier traversal. The exact scoreboard/tag names are implementation details, but no maze state may be global across all temples.

The maze only performs procedural generation while an eligible Survival/Adventure player is actively inside an uncleared labyrinth. Spectators do not start, advance, validate, or complete the maze.

## 6. Procedural shift generation

Every shift is derived at runtime from the **current** valid topology rather than selecting a canned target.

The generator uses randomized graph mutations:

1. Copy `CURRENT` topology into `NEXT`.
2. Randomly choose several currently closed internal edges and propose opening them.
3. Randomly choose several currently open edges and propose closing them.
4. Validate proposed closures against the `NEXT` graph.
5. Reject any closure that disconnects the graph, makes the sanctum unreachable, isolates an active player region, or violates route-quality constraints.
6. Continue proposing mutations until the shift reaches the required topology difference or the bounded attempt budget is exhausted.
7. Accept only a valid `NEXT` state.

Initial aggressive-shift target: approximately **16–28 changed passage edges** per cycle. This range is a tuning target. The implementation may reduce the number dynamically if performance or player-safety validation requires it, but a normal shift must visibly alter multiple regions rather than toggling one nearby doorway.

### 6.1 Solvability validation

The target topology must be programmatically validated before visible movement begins.

Validation must prove:

- entrance can reach sanctum;
- the traversable graph remains connected, preventing sealed unreachable cell islands;
- active players are not left in a region disconnected from the sanctum/entrance network;
- the shortest/validated route is not trivially short;
- the accepted target differs materially from `CURRENT`.

The path/flood-fill calculation must be time-sliced across ticks rather than running an unbounded command storm in one tick. Candidate generation has a hard attempt budget. If a sufficiently different valid topology is not found in time, the maze keeps the current valid layout and retries later; it never forces an invalid shift.

## 7. Shift timing and presentation

Normal cadence target:

- ~0–23 s: exploration in stable topology;
- ~23–27 s: next topology generation/validation runs quietly;
- ~27–30 s: warning sequence;
- ~30–34 s: physical wall movement;
- after movement: new topology becomes stable and the next cycle begins.

The exact cadence may be tuned, but the player-facing expectation remains roughly one major shift every 30 seconds.

Warning sequence should combine:

- distant deep stone grinding;
- low rumble increasing in intensity;
- dust/debris particles from ceiling seams and moving-wall tracks;
- chains/nearby details reacting where practical;
- localized stone scraping near panels that are about to move;
- a heavy lock/impact sound when the new state finishes.

The maze should communicate that a shift is coming before collision changes.

## 8. Moving-wall rendering and collision

Minecraft display entities do not provide solid player collision. The design therefore separates **smooth visual motion** from **authoritative collision**.

### 8.1 Stable state

When a passage is closed, it is represented by normal detailed wall blocks plus a hidden collision core/shell where needed. When open, the passage is physically empty and traversable.

### 8.2 Visual motion

When a wall changes state, its visible slab becomes temporary `block_display` geometry using interpolated transforms. The display must visibly travel between its architectural resting location and the passage; it must not appear at the destination instantly.

To avoid one massively stretched texture, a moving panel can be composed from a small bounded set of display strips/tiles. Displays exist only during shifts and are cleaned immediately afterward.

The default reusable wall mechanism is a large stone slab that **retracts upward into a roof cavity** when opening and descends from that cavity when closing. This integrates naturally with the required vaulted roofs and avoids needing a five-block side pocket at every grid edge. Selected major junctions may use sideways-moving slabs where the architecture provides a real recess.

### 8.3 Collision motion

Collision tracks the visible wall in discrete block steps because vanilla block collision cannot interpolate continuously.

- Opening: collision slices are removed progressively behind the visibly retracting slab.
- Closing: collision slices are introduced progressively behind the visibly descending/sliding slab.
- At the end of the animation, temporary collision/display state is replaced by the stable real-block state.

A closing generic maze wall must not silently suffocate a player. Before each collision slice advances, the system checks the sweep zone. If an eligible player occupies that slice, the wall pauses briefly. If it remains blocked beyond a bounded grace period, that particular closure is abandoned/reopened and the resulting topology remains more open, which is still solvable.

Purpose-built **crusher traps** are separate trap modules. They may damage players, but they require explicit telegraphing and are not used as the generic wall-safety behavior.

## 9. Safe topology transition

A valid target is not enough; the transition between valid states must also remain playable.

All shifts use **open-first, close-second** ordering:

1. Every connection that exists in `NEXT` but not `CURRENT` opens first.
2. The newly opened network becomes physically traversable.
3. Connections that exist in `CURRENT` but not `NEXT` then begin closing.
4. Occupied closing edges can delay or abort individually.
5. The final actually-achieved topology is committed as the new `CURRENT`.

This prevents a moment where the old solution is closed before the new solution exists.

## 10. Traps and authored hazards

Traps replace puzzle-room friction with environmental pressure. They are authored modules placed/armed in eligible maze cells or edges; their positions/state can vary with the procedural maze, but the trap mechanics themselves are intentionally designed rather than procedurally invented commands.

Initial trap families:

1. **Serpent Nest** — a wall recess opens and releases a small fragile snake group.
2. **Venom/Arrow Gallery** — wall slits telegraph and fire a timed projectile/poison lane.
3. **Lava Fissure** — a broken corridor or retracting safe floor exposes a traversable lava hazard with a fair route around/across it.
4. **Crusher Corridor** — heavily telegraphed wall slabs compress a specific trap lane, with a safe retreat/window.
5. **Gorgon Relief** — carved wall eyes glow and create a temporary gaze-style hazard that rewards looking away or using cover.
6. **Collapsing/Drop Route** — a floor section sends players into a lower bypass/side route rather than an unavoidable death pit.
7. **Petrified Expedition / Ambush** — environmental storytelling room that can reveal snakes or another short combat event after a shift.

Trap rules:

- traps never permanently modify the maze into an unsolvable state;
- a trap cannot occupy the only passage in a way that makes traversal impossible;
- newly armed traps receive readable telegraphing;
- traps clean up on instance reset/recovery;
- trap state is scoped to the temple instance;
- trap density is capped so several shifts do not turn every corridor into simultaneous damage spam.

## 11. Navigation readability

The maze is intentionally difficult, but it should not be visually homogeneous.

Use recurring districts/landmarks such as:

- a serpent-column gallery;
- a moss/root crypt district;
- a lava-cracked lower section;
- a petrified-expedition hall;
- a tall central junction with a distinctive roof;
- Gorgon relief corridors;
- progressively stronger green/serpent visual language near the sanctum.

The entrance and sanctum destination remain fixed. Players can build a mental sense of direction from architecture, but the route itself changes beneath that understanding.

No obvious arrow trail or minimap is required. Orientation comes from authored landmarks and the fixed destination, not puzzle instructions.

## 12. Completion and progression

The three old puzzle-complete flags no longer gate the Golden Gorgon Eye.

A temple instance gains a persistent `maze_cleared`-equivalent state when an eligible non-Spectator player legitimately crosses the sanctum completion threshold from the labyrinth side.

On first maze completion:

- the instance records the maze as cleared;
- further procedural shifting stops for that instance's first-clear route;
- the current topology freezes in a valid state so remaining teammates can reach the sanctum;
- the sanctum/arena approach opens permanently for that cleared temple;
- the Golden Gorgon Eye becomes available through the existing canonical Eye/pedestal state system.

Rematch rituals remain at the arena and do not require replaying the maze.

## 13. Multiplayer and instance isolation

Every maze cell, edge, temporary display, collision helper, trap controller, randomization pass, and completion state is scoped by `md_eid`.

Requirements:

- two temples can shift simultaneously without selectors touching the other instance;
- one temple's wall animation cannot remove/place another temple's collision blocks;
- late-arriving players in the same uncleared maze join the same topology rather than receiving a private maze;
- Spectators are ignored by generation, safety blocking, completion, and encounter scaling;
- disconnect/death cannot leave a wall permanently paused;
- if no eligible players remain in an uncleared maze, shifting pauses rather than burning server time.

## 14. Restart and failure recovery

Maze shifting uses a two-phase state model so a crash/restart cannot leave half a wall permanently moved.

The instance records a stable committed topology and a temporary in-progress target/shift phase.

On load/recovery:

- if no shift had committed, rebuild/restore the last committed `CURRENT` topology;
- if the commit completed, rebuild the committed new topology;
- remove orphan display/collision helpers from interrupted animations;
- re-arm only valid persistent trap state;
- never guess from whatever blocks happened to remain after the interrupted tick.

Recovery may visually snap after a server restart; smooth animation is required during live play, not across a process restart.

## 15. Performance constraints

The maze is large, but expensive work is event-driven rather than every tick.

- static temple construction should be split into scheduled build stages instead of one enormous function burst;
- procedural generation/path validation is time-sliced over multiple ticks;
- display entities exist only for currently moving wall modules;
- pathfinding markers are lightweight and scoped to the active instance;
- only a bounded number of wall modules animate per shift;
- shift generation pauses when no eligible player is in an uncleared maze;
- traps use bounded entity counts and explicit cleanup.

The implementation plan must include profiling/CI instrumentation for command/runtime regressions. Exact-runtime CI must continue rejecting parser/load/serialization errors.

## 16. Testing requirements

Implementation is not accepted solely because the generated functions parse.

### Static/contract tests

Require tests for:

- old mandatory puzzle progression removed from the critical path;
- maze footprint/grid constants match the approved large design;
- roof/ceiling modules are present for every traversable region;
- entrance and sanctum coordinates are fixed;
- all mutable topology entities/state are instance-scoped;
- no preset global state sequence is used;
- topology difference threshold exists;
- generation has a bounded retry/failure-safe path;
- open-first/close-second transition ordering;
- moving walls use interpolated displays rather than instant visual placement;
- temporary displays/collision helpers are cleaned;
- Spectators cannot complete or influence the maze;
- rematches do not require maze replay.

### Algorithm tests

A host-side reference/simulation test should generate many randomized topology transitions from many seeds and verify for every accepted state:

- entrance reaches sanctum;
- all cells are connected;
- minimum topology delta is satisfied;
- route is not trivially short;
- transitions never require closing before replacement openings exist;
- bounded-generation fallback preserves the previous valid state.

This simulation is a test oracle for the datapack algorithm, not a replacement for runtime testing.

### Exact Minecraft 26.1.2 runtime smoke

CI must exercise at least:

- direct-install datapack ZIP boot;
- creation of the large roofed labyrinth;
- initial topology validation;
- one successful randomized shift;
- proof that multiple wall edges changed;
- open-first transition marker before close phase;
- wall display spawn/interpolation data accepted by 26.1.2;
- final stable collision/block state;
- maze remains solvable after the shift;
- a simulated occupied closure safely delays/aborts instead of trapping the test actor;
- instance isolation with two maze instances;
- restart recovery from an in-progress shift;
- sanctum completion freezes the maze and unlocks first-clear progression.

### Manual client playtest

Dedicated-server CI cannot prove visual quality or human navigation feel. Before release, manually verify:

- no visible wall teleports during a live shift;
- wall audio/particles line up with movement;
- collision does not let players walk through a moving wall or get unfairly suffocated;
- roofs are complete and visually authored in Spectator;
- corridors are not visually repetitive;
- aggressive shifts are noticeable across multiple regions;
- the maze remains hard but understandable enough to finish;
- traps are readable and not unavoidable damage;
- multiplayer players see the same wall motion and topology.

## 17. Non-goals

This redesign does **not** add natural world generation, replace the approved Medusa boss mechanics, change the Staff/reward economy, or turn the dungeon into a command-block/redstone puzzle map.

It also does not require literal continuous collision interpolation, which vanilla display entities cannot provide. The requirement is smooth visible wall motion plus authoritative collision that tracks that motion safely in discrete block steps.

## 18. Acceptance summary

The redesign is successful when the first-clear dungeon feels like one coherent hostile machine: players descend into a detailed, fully roofed ~95×95 Gorgon labyrinth; the topology is generated and mutated at runtime rather than cycling through presets; roughly every 30 seconds the structure warns, rumbles, and physically shifts; the program rejects unsolvable states; new routes open before old routes close; moving walls animate smoothly while collision follows safely; traps and landmarks create variety; reaching the fixed sanctum ends the maze phase and leads directly into the existing Medusa encounter.
