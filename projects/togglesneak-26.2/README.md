# ToggleSneak

ToggleSneak is a small client-side Fabric mod for Minecraft 26.2. It adds a vanilla-style toggle crouch with an adaptive hold-to-lock gesture, while leaving the normal Sneak key functional.

## Compatibility

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5 or newer compatible 26.2 loader
- Fabric API 0.160.0+26.2 or newer compatible 26.2 API
- Client side only

The project uses Minecraft 26.2's Mojang mappings through Fabric Loom and builds against the real Minecraft/Fabric dependencies.

## Controls

The default Toggle Sneak key is backslash:

`\`

It appears in Minecraft's normal **Options → Controls → Key Binds** menu under **Toggle Sneak**, so it can be rebound normally.

- Press once: Toggle Sneak ON
- Press again: Toggle Sneak OFF
- Holding the key does not repeatedly toggle it.
- Pressing the normal Sneak key while Toggle Sneak is ON immediately turns the toggle OFF.

The mod does not fake a permanently pressed Sneak key. It changes only the resulting local movement-input sneak bit after vanilla has built the input for that tick.

## Smart Latch

Hold the normal Sneak key to start a Smart Latch attempt.

The latch accumulates confidence rather than using one fixed timer:

- Actively moving: about 3.5 seconds.
- Standing still: about 8 seconds.
- If movement changes during the hold, the accumulation rate blends smoothly. Existing progress is not discarded.
- Mining/attacking or holding Use/place/interact marks that physical Sneak press as busy, so Smart Latch cannot trigger from it.
- Once Attack/Use occurs during a Sneak hold, Smart Latch stays blocked until Sneak is released. This prevents a long crouch-mining/building hold from unexpectedly latching immediately after the interaction stops.

Short taps show no latch HUD. Once the hold is clearly intentional, a small message appears above the hotbar, for example:

`Hold Sneak to lock: 2.4s`

When enough confidence has accumulated, the mod does **not** toggle while the key is still held. The message changes to:

`Release Sneak to lock`

Only releasing the normal Sneak key locks Toggle Sneak ON.

If Toggle Sneak was already ON and that physical Sneak press turned it OFF, the same held press is deliberately ineligible for Smart Latch. Release Sneak first, then start a fresh hold.

## GUI behavior

Minecraft 26.2 routes active screens through its GUI object. ToggleSneak checks the actual 26.2 screen state and follows vanilla-like GUI behavior:

- Opening inventory, chat, containers, pause/options, crafting, or another screen suspends the mod's forced sneak input.
- The logical Toggle Sneak state is preserved.
- Closing the screen resumes forced sneak if the logical toggle is still ON.
- An in-progress Smart Latch is cancelled when a GUI opens.
- Holding Sneak through the GUI cannot silently resume the same Smart Latch attempt after the GUI closes.

Forced sneak is also suspended while riding so the mod does not turn crouch into repeated dismount input.

## HUD

Status text is rendered with Minecraft's font as a custom HUD element just above the hotbar:

- white text
- normal subtle text shadow
- no panel/background
- ~180 ms fade-in with a small upward slide
- ~300 ms fade-out

`Toggle Sneak: ON` remains subtly visible while active. `Toggle Sneak: OFF` is temporary.

The HUD renders direct strings through the Minecraft 26.2 GUI extractor. It does not use chat, titles, or Minecraft text-component construction.

## Resets

ToggleSneak clears all logical and Smart Latch state on:

- player death
- disconnect
- player/world replacement, including world changes/respawns

No state is carried between sessions/worlds.

## Build

From this project directory:

```bash
./gradlew clean check build
```

The final remapped mod JAR is created under `build/libs/`.

The `check` task also runs the state simulation suite covering toggle edge detection, Smart Latch timing/transitions, GUI cancellation, release-to-lock, reset behavior, and HUD animation.

## Implementation notes

The runtime implementation is intentionally small:

- `ToggleSneakClient`: key binding, current client/session state, HUD registration.
- `ToggleSneakState`: pure Smart Latch/toggle state machine.
- `MinecraftMixin`: one client-tick hook, avoiding lifecycle tick-event ABI assumptions.
- `KeyboardInputMixin`: ORs logical toggle sneak into the real 26.2 movement-input record while preserving all other movement bits.
- `ToggleSneakHud` + `HudAnimationModel`: minimal direct-string HUD rendering/animation.

There are no config libraries, config screens, packet loops, or anti-cheat bypass features.
