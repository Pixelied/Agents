# Predictive Elytra Mace Swap (Minecraft Java 26.3)

Standalone **client-side** Fabric mod. The mod predicts a short-term mace-smash window while you fly with Elytra and **hold a mace**, equips a chestplate only when the projected trajectory permits a legal vanilla mace smash, then restores Elytra and attempts to restart flight.

## Requirements

- Minecraft **Java Edition 26.3**, Java **25**
- Fabric Loader **0.19.5+** and Fabric API **0.163.0+26.3**
- Optional (for its Config button): Mod Menu **21.0.0**
- A usable Elytra equipped, a mace anywhere in your hotbar, and a chestplate in your main inventory or hotbar

## Install

1. Install Fabric Loader for Java 26.3 and place Fabric API 0.163.0+26.3 into `mods`.
2. Put `predictive-elytra-mace-swap-1.0.2.jar` into your Minecraft `mods` directory. **Don't use** the `-sources.jar`.
3. Install Mod Menu 21.0.0 for a Configure button, or omit it and keep the conservative defaults.
4. Launch the Fabric 26.3 profile. Open **Mods → Predictive Elytra Mace Swap → Configure**.
5. Equip Elytra; keep your chestplate in inventory or hotbar and a mace anywhere in your hotbar. Fly toward a target. The mod selects the mace only after the chestplate swap is confirmed; you still attack manually.

## Behavior and conservative assumptions

- Uses `MaceItem.canSmashAttack` rule (`fallDistance > 1.5` and no gliding), the 26.3 glide lift/drag equations, 26.3 item attack range, target hitboxes, predicted remote velocity, obstructing blocks, and measured ping. The prediction runs only within a configurable nearby area.
- **Swap timing is adjustable** in Mods → Configure → Prediction. The default is 3 ticks after the predicted glide exit; lower values wait until the smash is closer, while higher values start preparing earlier. If set too low for your trajectory or ping, the mod may skip an opportunity rather than swap early.
- Does **not** change your aiming, movement, attack binding, or send attack packets. If the mace is not selected, it selects the hotbar mace after the chestplate swap is confirmed, then restores your previous selected slot during recovery unless you manually change slots. The temporary inventory buffer never uses the chosen mace slot.
- Uses vanilla `ContainerInput.SWAP` operations with empty cursor; it never performs cursor pickup/drop operations. Chestplate can be in main inventory: a *temporary* unselected hotbar slot is reserved, then returned to its initial contents after the attack.
- Its state machine checks each predicted client inventory transition before proceeding; when a match is unexpected, it stops instead of blindly swapping and warns you.
- On attack input, expiry, invalidated target, or disabled setting, reverses inventory changes. Re-glide sends the same START_FALL_FLYING action as a Space press as soon as the client observes Elytra restored. It cannot guarantee recovery if the player has already touched the ground or if a server plugin rejects flight.
- Measured server ping is an estimate, **not an acknowledgment** of transaction acceptance. Future remote player movement and server anticheat rules are unknowable to a client. Treat this as a conservative aid, not a guaranteed hit bot.
- Default `Require aim` is on. The prediction assumes the current look direction remains stable, so it avoids swapping when a valid future ray against a projected hitbox cannot be found.
- Configuration persists as `config/predictive-elytra-mace-swap.json`.

## Build

```sh
gradle --no-daemon clean test build
```

Requires Gradle 9.7 and a Java 25 JDK. This release is version 1.0.2 and supports a mace anywhere in the hotbar. CI creates the installable mod jar as a GitHub Actions artifact and also keeps the source jar. The Gradle setup uses the 26.3 deobfuscated (`net.fabricmc.fabric-loom`) toolchain.

## Validated vs not validated

- `TrajectoryMathTest`: strict smash threshold, gravitational fall-distance accumulation, velocity and latency calculations.
- CI Gradle checks compilation and packaging with the actual 26.3 Fabric dependencies.
- **Not in-game tested:** multiplayer attack packet timing, inventory acknowledgement under high ping, Mod Menu rendering on different GUI scales, crash behavior on modpack-specific combinations, server-specific anticheat.

## Source provenance

Designed after inspecting the supplied 26.3 client source (especially `MaceItem`, `LivingEntity`, `Entity`, `InventoryMenu`, `AbstractContainerMenu`, `MultiPlayerGameMode`, and `LocalPlayer`). No proprietary Minecraft source files or classes are included in this repository or build artifact. MIT applies only to original mod code.
