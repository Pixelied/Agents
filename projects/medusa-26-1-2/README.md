# Medusa 26.1.2

Datapack-driven multiplayer Medusa temple, labyrinth, and boss encounter for Minecraft Java 26.1.2.

- Gameplay: vanilla datapack only
- Visuals: optional resource pack; ETF/EMF is used only for the enhanced Medusa model
- Runtime: Java 25
- Datapack format: 101.1
- Resource-pack format: 84.0
- World generation: explicit admin placement in this first release; natural generation is intentionally deferred

## Install

Copy `datapacks/medusa` into the world's `datapacks/` directory. Copy `resourcepacks/medusa` to clients that want the enhanced visuals. The gameplay pack remains functional if the resource pack, ETF, or EMF is absent.

Use the exact Minecraft Java 26.1.2 server/client. The dedicated server requires Java 25.

## Place a temple

Run this as an administrator at the intended temple origin:

```mcfunction
/function medusa:admin/place_temple
```

Each placed temple owns an independent encounter ID, dungeon state, boss, participants, rewards, Eye state, and cleanup state. Multiple temples are designed to coexist on one server.

## Encounter flow

The fixed critical route is:

`Surface Temple → Underground Descent → Labyrinth → Inner Lair → Medusa Arena`

The labyrinth contains the three required Gorgon puzzles: **Averted Eyes**, **Borrowed Gaze**, and **The Blind Passage**. Optional branches contain side treasure/traps but are never required to guess the main route.

At the arena, taking the **Golden Gorgon Eye** from its pedestal seals the room and releases Medusa from the central stone prison. After a victory, the Eye becomes the reusable rematch key.

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

## Rematch ritual

After Medusa has been defeated, interact with the arena pedestal to retrieve/replace the canonical **Golden Gorgon Eye**. With the Eye held, the rematch offering is:

- 4 Gorgon Scales
- 1 Serpent Fang

The offering is consumed when the ritual commits. The Eye is treated as the seal key and is restored to the pedestal after the next victory rather than being permanently consumed.

## Debug / runtime smoke commands

These functions exist for exact-version CI and local server diagnosis:

```mcfunction
/function medusa:debug/create_test_temple
/function medusa:debug/start_test_boss
/function medusa:debug/give_test_items
/function medusa:debug/test_petrification_damage
```

## Verification

Static checks:

```bash
python3 -m unittest discover -s tests -v
python3 scripts/validate_medusa.py
```

GitHub Actions additionally downloads Mojang's exact 26.1.2 dedicated server, verifies its SHA-1 and Java-25 requirement, boots the pack, exercises the debug entrypoints, checks that the temple instance and Medusa spawn, and verifies the controlled petrification-suffocation damage window removes Resistance only around the custom damage call and restores it immediately afterward.

## Manual acceptance gates

Before a release is treated as gameplay-complete, verify all of the following on an exact 26.1.2 server:

- solo first clear
- 2-player first clear
- 4+ player first clear
- late join during phase 2
- partial-petrification death cleanup
- full-petrification death/disconnect cleanup
- last-second 12-point rescue
- party wipe in phases 1, 2, and 3
- restart during awakening, active, defeated, and `ritual_ready`
- Golden Gorgon Eye loss/recovery
- rematch ritual atomic success/failure
- Staff tap pulse
- Staff 3-second full channel
- Staff interruption by LOS, caster damage, and aim loss
- PvP petrification auto-release with no free instant kill
- boss target 1.5-second auto-release with no Staff suffocation
- 64→0 Staff depletion
- 8-charge Scale recharge and 64 cap
- 4-charge Stone Spikes with no block edits
- resource pack disabled fallback
- multiple simultaneous temple instances
- inactive-instance tick-cost sanity
