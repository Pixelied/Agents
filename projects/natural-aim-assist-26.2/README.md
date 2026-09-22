# Natural Aim Assist

Natural Aim Assist is a lightweight client-side Fabric mod for Minecraft Java 26.2. It adds configurable aim assistance that layers correction over the player's own camera movement rather than hard-locking the view.

## Target

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5+
- Fabric API 0.160.0+26.2
- Optional Mod Menu 20.0.2 integration

## 1.1.0 behavior fix

Version 1.1.0 fixes two reasons the first release could feel inactive:

- **Require Attack** and **Pause Mining / Use** no longer self-cancel when holding attack while the crosshair is still on a block.
- High mouse speed no longer crushes correction strength to a nearly invisible level.

The controller also has a client-tick fallback so assistance continues to update when the mouse-turn hook is idle.

## Configuration

With Mod Menu installed, open **Mods -> Natural Aim Assist -> Configure**.

Numeric options now use a slider **and** an editable value box. Hostile and passive mobs are separate target filters:

- Strength: 0-100%
- Assist FOV: 1-360 degrees
- Range: 1-12 blocks

Defaults:

- Enabled
- Natural preset
- 50% strength
- 30 degree assist FOV
- 4.5 block range
- Vertical assist on
- Require Attack on
- Weapons Only on
- Target Players on
- Hostile Mobs on
- Passive Mobs off
- Visible Only on
- Ignore Invisible on
- Pause Mining / Use on

The file `config/naturalaim.properties` stores settings.

## Controller

The module preserves target commitment, acceleration/deceleration limits, a torso-biased dynamic aim region, distance scaling, and deliberate pull-away disengagement. There is intentionally no random per-frame jitter.

## Build

Use Gradle 9.5.1 with Java 25 and run `clean check build`. The remapped mod JAR is written to `build/libs/`.

See `PLAN.md` for the original controller architecture.
