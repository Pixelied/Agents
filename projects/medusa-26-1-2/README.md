# Medusa 26.1.2

Datapack-driven multiplayer Medusa temple, procedural shifting labyrinth, and boss encounter for Minecraft Java 26.1.2.

- Gameplay: vanilla datapack only
- Visuals: optional resource pack; ETF/EMF is used only for the enhanced Medusa model
- Runtime: Java 25
- Datapack format: 101.1
- Resource-pack format: 84.0
- World generation: explicit admin placement in this first release; natural generation is intentionally deferred

## Install

Copy `datapacks/medusa` into the world's `datapacks/` directory. Copy `resourcepacks/medusa` to clients that want the enhanced visuals. The gameplay pack remains functional if the resource pack, ETF, or EMF is absent.

Use the exact Minecraft Java 26.1.2 server/client. The dedicated server requires Java 25.

Do not leave stale copies of the Medusa packs beside the current ones. In particular, remove old `medusa/` folders or old `medusa.zip` files before testing a replacement build so Minecraft cannot load two revisions at once.

## Place a temple

Run this as an administrator at the intended temple origin:

```mcfunction
/function medusa:admin/place_temple
```

Each placed temple owns an independent encounter ID, maze topology, shifting-wall helpers, traps, dungeon state, boss, participants, rewards, Eye state, and cleanup state. Multiple temples are designed to coexist on one server.

## Encounter flow

The first-clear route is:

`Surface Temple → Underground Descent → Shifting Labyrinth → Fixed Sanctum → Medusa Arena`

The old three-puzzle progression has been removed. The labyrinth itself is now the dungeon mechanic.

### Shifting labyrinth

The labyrinth is approximately **95x95 blocks** and uses a **13x13 logical cell graph** under a fully authored roof system. The entrance and the fixed sanctum destination never move, but the route between them is generated at runtime rather than selected from a finite list of layouts.

While an eligible Survival or Adventure player is exploring an uncleared maze, the labyrinth prepares a new randomized topology and shifts roughly every **30 seconds**. Candidate layouts are validated before they can affect the world: the full graph must remain connected, the sanctum must remain reachable, the route cannot collapse into a trivial shortcut, and an accepted shift must change a meaningful number of passage edges.

A shift happens in a safe order:

1. Stone grinding, particles, and local warning cues announce the change.
2. New passages open first.
3. Only after those openings are available do old passages begin closing.
4. Moving walls use interpolated `block_display` slabs for visible motion while real barrier/block collision follows the slab in steps.
5. If a generic closing wall remains occupied, it waits and then aborts/retracts instead of silently suffocating the player.
6. The achieved topology is committed only after the transition completes.

The maze is not a flat test box. Every intended interior is roofed. Corridors use rib/barrel-vault architecture, mechanical retraction cavities, ceiling ribs, chains, lanterns, and varied stonework. Larger landmarks include a serpent-column gallery, moss/root crypt, lava-cracked district, petrified expedition, tall central junction, and Gorgon-relief corridor.

Up to four authored hazards can be armed in one temple at a time. The available families are Serpent Nest, Venom Gallery, Lava Fissure, Crusher, Gorgon Relief, Drop Route, and Petrified Expedition. Traps are instance-scoped and reroll after stable shifts; they are environmental hazards, not mandatory puzzle locks.

Reaching the sanctum legitimately completes the labyrinth for that temple. The maze freezes into a safe completed state and the Golden Gorgon Eye becomes available. Rematches do **not** require replaying the labyrinth.

## Medusa arena and Eye

At the arena, taking the **Golden Gorgon Eye** from its pedestal seals the room and releases Medusa from the central stone prison. The Eye carries its temple instance ID so an Eye from one temple cannot unlock another temple's ritual.

After a victory, the Eye becomes the reusable rematch key and is returned through the existing recovery lifecycle if necessary.

## Medusa Staff

The Staff recipe uses exactly:

- 1 Medusa's Heart
- 4 Gorgon Scales
- 1 Serpent Fang
- 1 Netherite Ingot
- 1 Breeze Rod

The Staff holds **64 Gorgon Charges**. One Gorgon Scale restores **8 charges** up to the cap.

Controls:

- Normal use: immediate 1-charge petrification pulse; keep holding to channel the same target.
- About 3 seconds of uninterrupted channeling: full petrification.
- Continued channeling beyond about 5 seconds: crushing/suffocation damage on non-boss targets.
- Sneak + use with a Gorgon Scale in the offhand: recharge 8 charges.
- Sneak + use without a Scale in the offhand: cast **Stone Spikes** for 4 charges.

Channeling breaks if line of sight/aim lock is lost or the caster takes damage. The caster is heavily slowed while channeling. Boss-class targets crack free quickly and never receive the Staff's suffocation finisher.

Temporary Staff and Medusa petrification overlays do not broadly clear unrelated potion/beacon effects. Medusa's custom petrification damage is registered to bypass Resistance directly, so damage testing does not need to remove and restore a target's pre-existing Resistance effect.

## Rematch ritual

After Medusa has been defeated, interact with the arena pedestal to retrieve/replace the canonical **Golden Gorgon Eye**. With the Eye held, the rematch offering is:

- 4 Gorgon Scales
- 1 Serpent Fang

The offering is escrowed during awakening and finalized only when Medusa actually spawns. If the encounter is interrupted before the spawn commits, recovery refunds the pending offering instead of silently deleting it. The Eye is treated as the seal key and is restored to the pedestal after the next victory rather than being permanently consumed.

## Debug / runtime smoke commands

These functions exist for exact-version CI and local diagnosis:

```mcfunction
/function medusa:debug/create_test_temple
/function medusa:debug/start_test_boss
/function medusa:debug/give_test_items
/function medusa:debug/test_petrification_damage
/function medusa:debug/toggle_gaze_diagnostics
```

`medusa:debug/create_test_temple` drives the exact-runtime maze smoke used by CI: 169-cell creation, initial solvability, randomized delta, open-before-close ordering, moving-wall display creation, authoritative collision, occupied-wall abort, instance isolation, restart recovery, legitimate completion, and the existing boss/Staff/lifecycle smoke sequence.

## Verification

Static checks:

```bash
python3 -m unittest discover -s tests -v
python3 scripts/validate_medusa.py
python3 scripts/generate_temple.py --check
```

GitHub Actions additionally downloads Mojang's exact 26.1.2 dedicated server, verifies its SHA-1 and Java-25 requirement, boots the same direct-install datapack ZIP delivered to users, and requires the maze, boss, gaze, Staff, Golden Eye, lifecycle, recovery, and parser/load smoke markers to pass without datapack load/serialization errors.

The host-side maze oracle stress-tests many generated layouts and repeated shifts. Runtime validation remains authoritative for Minecraft command behavior.

## Manual acceptance gates

Automated server tests cannot prove client rendering quality or human navigation feel. Before a release is treated as gameplay-complete, verify all of the following on exact Minecraft 26.1.2:

### Labyrinth presentation and movement

- Spectator inspection confirms the complete labyrinth, sanctum, approach, and major rooms have intentional roofs; no accidental open-top rooms or giant flat developer lids.
- Corridors and landmarks have enough material/shape variation to remain visually readable rather than repeating identical stone boxes.
- Observe several full shifts: moving walls visibly interpolate through their tracks and do not pop or teleport to the destination.
- Collision advances/retracts with the visible moving walls; players cannot simply walk through a moving `block_display`.
- Standing in a closing sweep volume causes the wall to wait and eventually retract safely rather than trapping or suffocating the player.
- Opening passages are usable before closing passages remove the previous route.
- Stone-grind sound, impacts, dust, and ceiling/wall particles line up with the wall that is actually moving.
- Multiple shifts visibly alter multiple maze regions and do not collapse into an obvious repeating state sequence.
- The fixed sanctum remains understandable as the overall destination even while local routes change.
- First-clear navigation is difficult and disorienting without becoming directionless; record approximate completion time and any landmark that is too weak or too revealing.

### Trap acceptance

- Every trap family has a readable telegraph before its dangerous phase.
- Serpent Nest, Venom Gallery, Lava Fissure, Crusher, Gorgon Relief, Drop Route, and Petrified Expedition each function without permanently blocking the logical route.
- No more than four trap modules are armed at one temple at the same time.
- Trap helpers from one temple never affect another nearby temple.

### Multiplayer and recovery

- solo first clear
- 2-player first clear with synchronized wall motion
- 4+ player first clear with synchronized wall motion
- players on opposite sides of the maze during one shift
- two simultaneous temple instances shifting independently
- Spectator players do not start, advance, block, complete, or scale the maze
- restart during initial generation, warning, opening, and closing phases
- recovery removes transient displays/barriers/helpers and rebuilds the committed topology
- rematch after first clear skips maze replay
- Golden Gorgon Eye loss/recovery and cross-temple identity rejection
- restart during awakening, active boss, defeated, and `ritual_ready`
- interrupted pre-spawn rematch offering refunds correctly

### Boss and Staff regression

- late join during boss phase 2
- partial-petrification death cleanup
- full-petrification death/disconnect cleanup
- last-second 12-point rescue
- party wipe in phases 1, 2, and 3
- Staff tap pulse
- Staff 3-second full channel
- Staff interruption by LOS, caster damage, and aim loss
- PvP petrification auto-release with no free instant kill
- boss target 1.5-second auto-release with no Staff suffocation
- 64→0 Staff depletion
- 8-charge Scale recharge and 64 cap
- 4-charge Stone Spikes with no block edits

### Client/resource-pack and performance

- resource pack disabled fallback remains playable
- ETF/EMF enabled Medusa model renders correctly from every major angle
- no stale duplicate `medusa` resource pack or malformed old ZIP is present during the test
- ordinary maze shifts do not produce sustained server `Can't keep up!` warnings
- inactive temples do not continuously generate/validate maze candidates
- active wall-display/helper count remains bounded to changed passages, not the entire 169-cell maze
- one-time admin placement cost and ordinary shift cost are measured separately
