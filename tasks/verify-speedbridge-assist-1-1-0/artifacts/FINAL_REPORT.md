# SpeedBridge Assist 1.1.0 — Final CI Verification

## Outcome

SpeedBridge Assist 1.1.0 successfully compiled, tested, packaged, and started in a headless Minecraft 26.2 development client using GitHub Actions and Temurin Java 25.

## Verified environment

- Minecraft: 26.2
- Fabric Loader: 0.19.3
- Fabric API: 0.156.0+26.2
- Fabric Loom: 1.17.17
- Gradle: 9.6.1
- Mod Menu: 20.0.1
- Java: Temurin 25.0.3+9
- Runner: Ubuntu 24.04

## Automated results

- Agents workspace: 22 tests passed; validator returned `ok: true`.
- Standalone SpeedBridge domain suite: 224 assertions passed.
- Minecraft 26.2 automatic-bridge source guard passed.
- Gradle JUnit suite: 54 tests, 0 failures, 0 errors, 0 skipped across 16 suites.
- `./gradlew clean test --stacktrace --no-daemon`: `BUILD SUCCESSFUL`.
- `./gradlew clean build --stacktrace --no-daemon`: `BUILD SUCCESSFUL`.
- Production JAR ZIP integrity check passed.
- Fabric metadata inside the production JAR identifies `speedbridge` version `1.1.0` with the expected client and Mod Menu entrypoints.
- Minecraft loaded 52 mods, including `speedbridge 1.1.0`.
- The render thread initialized LWJGL, reloaded resources, and created texture atlases.
- No SpeedBridge-specific exception, failed entrypoint, mixin-application failure, mod-resolution failure, or crash report was found.
- The development client survived the entire 120-second smoke window and was intentionally terminated by `timeout` with exit code 124.

## Artifacts and digests

- Verified workflow run: `30704868221`
- Production JAR artifact: `8820035651`
- Verification evidence artifact: `8820035778`
- Source archive SHA-256: `59e6411915ec06b2cc9d26a6c30bf1b6da5dcd53f0ef8f830dbf61424654aa22`
- Production JAR SHA-256: `b6978b38795a480bc644413847c8ec81a50b7ab830b459a8e7c054e2150f74d8`
- Production artifact ZIP SHA-256: `ea6f966313a1f59647ceaf1e30b39880a99bea4ff61c6f7f1eb750ae644c2bc7`
- Verification artifact ZIP SHA-256: `6fc9d6af1f559887cd89ecba94e84d556f5aa3bb47d343479cd95a2de3c7bfc7`
- Merged PR: `#2`
- Squash merge commit: `4383b1b72892277979855413cace14257693c10d`

## Environment-only warnings

The headless CI runner had no Microsoft account session, narrator native library, or audio device. Minecraft logged authentication HTTP 401 responses, a missing `flite` narrator library, and OpenAL device failure. It disabled unavailable services and continued rendering. These warnings were not caused by SpeedBridge Assist.

## Not verified by CI

Automated compilation and startup do not prove physical bridge execution inside a world. The following still require hands-on testing:

- Straight, diagonal, upward, and diagonal-upward bridging while moving in a real world.
- Actual jump, sneak, camera, and placement timing against live player physics.
- Transitions between horizontal patterns and staircase cycles.
- Full blocks, slabs, stairs, paths, snow layers, and other irregular collision shapes in gameplay.
- Real multiplayer latency, low FPS, server corrections, and anti-cheat behavior.
- Manual input override and recovery during unexpected live-world failures.

The mod is therefore build-verified and startup-verified, but not yet gameplay-verified.
