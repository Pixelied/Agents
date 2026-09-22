# Natural Aim Assist 26.2 - implementation plan

## Goal

Build a client-side Fabric mod for Minecraft Java 26.2 that behaves like a small correction layer over real mouse input rather than an automated camera lock. The player remains the source of the turn; the module only helps an already-plausible movement settle onto a nearby valid target.

## Design rules

1. Player input stays primary: vanilla mouse handling runs first, then only a bounded correction is added.
2. No hard locking: never set the camera directly to a target angle or select a distant target just because one exists.
3. No fake jitter: natural behavior comes from context, acceleration/deceleration, acquisition, target regions, and disengagement.
4. Target regions, not points: preserve natural shoulder/torso aim rather than dragging everything to exact center.
5. Respect intent immediately: meaningful mouse movement away from the target drops assistance and briefly suppresses reacquisition.
6. Context-aware defaults: no help in GUIs, while mining/using, through walls, or outside configured combat conditions.

## Phase 1 - mouse intent

Inject at the tail of `MouseHandler.turnPlayer(double)`. Compare the next vanilla-produced yaw/pitch with the previous final yaw/pitch to recover the player's real mouse movement. Use monotonic frame timing and reset on session changes.

## Phase 2 - targeting and commitment

Search only within range and FOV. Default to visible non-creative, non-spectator players. Keep an already-valid target instead of reselecting every frame, and scan candidates at a short interval to reduce churn.

## Phase 3 - dynamic aim region

Inset the target bounding box horizontally and use a torso-biased vertical band. Project the current look ray to target depth and clamp that projection into the region. When the crosshair already passes through the region, error approaches zero rather than being pulled toward a synthetic center.

## Phase 4 - correction controller

Use wrapped yaw/pitch error plus the dot product between real mouse input and target error. Pull-away cancels immediately. Fast flicks heavily reduce assist until they slow. Acquisition ramps in over roughly 120 ms. Convert remaining error to angular velocity and enforce velocity, acceleration, deceleration, deadzone, and no-overshoot limits.

Presets tune those internal limits; the user-facing Strength control remains simple.

## Phase 5 - combat gates

Defaults:
- enabled: true
- Natural preset
- strength: 50%
- FOV: 10 degrees
- range: 4.5 blocks
- vertical assist: true
- require attack: true
- weapons only: true
- target players: true
- target mobs: false
- visible only: true
- ignore invisible: true
- pause while mining/using: true

Weapons Only recognizes 26.2 swords, axes, spears, maces, and tridents.

## Phase 6 - config and Mod Menu

Persist a small properties file in Fabric Loader's config directory. Use the native 26.2 Screen/GuiGraphicsExtractor API for the settings UI. Mod Menu is optional at runtime; when installed it provides the Configure entry.

## Phase 7 - verification

Run deterministic simulations for wrapping, smoothing, acceleration approach, intent classification, region clamping, and no-overshoot behavior. CI then builds with Java 25 / Gradle 9.5.1, launches a real Fabric 26.2 client under Xvfb, fails on mixin/linkage/crash signatures, inspects the remapped JAR metadata, and uploads the final JAR artifact.
