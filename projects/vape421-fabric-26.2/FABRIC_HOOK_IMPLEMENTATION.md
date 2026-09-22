# Fabric 26.2 hook implementation status

This file tracks concrete hook code in `fabric26_2`, separately from the broader parity matrix.

## Implemented, awaiting Java 25/Loom runtime test

- `ClientTickEvents.START_CLIENT_TICK` -> `EventThreadBoundPreTick`, then `EventPreTick`.
- `ClientTickEvents.END_CLIENT_TICK` -> `EventPostTick`, then `EventThreadBoundPostTick`.
- `Minecraft#setScreen` HEAD Mixin -> recovered `EventGuiOpen` at the same semantic entry point as the legacy transformer.
- `LocalPlayer#tick` HEAD/RETURN Mixins -> recovered `EventPreLocalPlayerTick` / `EventPostLocalPlayerTick`.
- `Connection#send(Packet, ChannelFutureListener, boolean)` -> recovered `EventPacketSend`, preserving cancellation and packet replacement.
- `Connection#channelRead0(ChannelHandlerContext, Packet)` -> recovered `EventPacketReceive`, preserving cancellation and packet replacement.
- Packet replacement uses a per-thread re-entry guard so replaying a modified packet through vanilla does not double-fire the recovered event.
- Fabric client lifecycle owns bootstrap and shutdown state; injector/JNI bootstrap is not used.
- `RecoveredEventBridge` instantiates the existing recovered event classes and calls their existing `fire()` methods; it is not a replacement event bus.
- The Fabric client source set points at the original recovered `../src/main/java` tree so Loom will compile the real client rather than a copied subset.

## Intentionally not wired yet

- Full recovered-core startup from the Fabric entrypoint until the initialization path has been runtime-checked under Java 25.
- `EventRender2D.create()` until its GL11-era primitives are ported to 26.2 rendering/extraction APIs.
- Local-player move mutation until the Fabric Mixin preserves vector replacement/cancellation semantics.

## Verified 26.2 hook targets

- Fabric `ClientTickEvents.START_CLIENT_TICK` / `END_CLIENT_TICK`.
- `Minecraft#setScreen(Screen)`.
- `LocalPlayer#tick()`.
- `Connection#send(Packet, ChannelFutureListener, boolean)`.
- `Connection#channelRead0(ChannelHandlerContext, Packet)`.
