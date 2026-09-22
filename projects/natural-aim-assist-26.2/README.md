# Natural Aim Assist

Natural Aim Assist is a lightweight client-side Fabric mod for Minecraft Java 26.2. It adds a configurable aim-assistance layer that works with the player's real mouse movement instead of replacing it with a target lock.

## Target

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5+
- Fabric API 0.160.0+26.2
- Optional Mod Menu 20.0.2 integration

## What makes it different

The module runs after vanilla mouse input and measures the turn the player actually made. Assistance is then bounded by target alignment, mouse intent, angular acceleration, target commitment, acquisition ramp, distance, and combat context. Pulling the mouse away immediately disengages the controller and creates a short reacquisition cooldown.

A dynamic inner torso region is used instead of one exact chest/head coordinate, so naturally landing on a shoulder or another useful part of the hitbox does not cause an artificial snap toward center.

There is intentionally no random per-frame jitter.

## Configuration

With Mod Menu installed, open **Mods -> Natural Aim Assist -> Configure**.

Defaults are conservative: Natural preset, 50% strength, 10 degree FOV, 4.5 block range, attack required, weapons only, visible players only, and pause while mining/using.

The file `config/naturalaim.properties` stores settings.

## Build

Use Gradle 9.5.1 with Java 25 and run `clean check build`. The remapped mod JAR is written to `build/libs/`.

See `PLAN.md` for the controller design and verification plan.
