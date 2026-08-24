# Fallen Knight 26.1.2

Datapack-driven multiplayer Fallen Knight boss project for Minecraft Java 26.1.2.

## Install / test

Use the release builder or the CI artifact. A release contains two ready-to-install ZIPs:

- `Fallen-Knight-Datapack-26.1.2.zip` -> put directly in `<world>/datapacks/`
- `Fallen-Knight-Resource-Pack-26.1.2.zip` -> put directly in `.minecraft/resourcepacks/`

Do not re-zip the inner folders. Both ZIP files contain `pack.mcmeta` at their root.

For the custom boss appearance, clients need Entity Model Features (EMF) and Entity Texture Features (ETF). Gameplay remains datapack-driven.

After entering the world, run `/reload`, then use exactly:

`/function fallen_knight:debug/start_test_fight`

Reset with:

`/function fallen_knight:debug/cleanup_test_fights`

Functions under `fallen_knight:arena/*` are internal implementation functions; several are macros and cannot be called directly without arguments.
