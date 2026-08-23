#!/usr/bin/env bash
set -euo pipefail
CLASSES="${1:?classes dir}"
DUMP=$(mktemp)
trap 'rm -f "$DUMP"' EXIT
mapfile -t CLASS_FILES < <(find "$CLASSES/studio/pixelied/pearlcatch" -name '*.class' | sort)
CLASSES_TO_DUMP=()
for c in "${CLASS_FILES[@]}"; do
  rel=${c#"$CLASSES/"}; cls=${rel%.class}; cls=${cls//\//.}
  CLASSES_TO_DUMP+=("$cls")
done
javap -classpath "$CLASSES" -v "${CLASSES_TO_DUMP[@]}" > "$DUMP"
need() { grep -F "$1" "$DUMP" >/dev/null || { echo "MISSING ABI: $1"; exit 1; }; }
forbid() { if grep -F "$1" "$DUMP" >/dev/null; then echo "FORBIDDEN ABI: $1"; exit 1; fi; }

grep -E 'InterfaceMethodref.*// net/fabricmc/loader/api/FabricLoader\.getInstance:\(\)Lnet/fabricmc/loader/api/FabricLoader;' "$DUMP" >/dev/null || { echo 'MISSING ABI: FabricLoader.getInstance must be InterfaceMethodref'; exit 1; }
need '// net/fabricmc/loader/api/FabricLoader.getInstance:()Lnet/fabricmc/loader/api/FabricLoader;'
need '// net/fabricmc/loader/api/FabricLoader.getConfigDir:()Ljava/nio/file/Path;'
need '// net/fabricmc/loader/api/FabricLoader.getGameDir:()Ljava/nio/file/Path;'
need '// com/google/gson/Gson.toJson:(Ljava/lang/Object;)Ljava/lang/String;'
need '// net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.START_CLIENT_TICK:Lnet/fabricmc/fabric/api/event/Event;'
need '// net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.END_CLIENT_TICK:Lnet/fabricmc/fabric/api/event/Event;'
need '// net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientEntityEvents.ENTITY_LOAD:Lnet/fabricmc/fabric/api/event/Event;'
grep -E 'Methodref.*// net/fabricmc/fabric/api/event/Event\.register:\(Ljava/lang/Object;\)V' "$DUMP" >/dev/null || { echo 'MISSING ABI: Event.register must be class Methodref'; exit 1; }
if grep -E 'InterfaceMethodref.*// net/fabricmc/fabric/api/event/Event\.register:' "$DUMP" >/dev/null; then echo 'FORBIDDEN ABI: Event.register encoded as interface method'; exit 1; fi

need '// net/minecraft/client/Minecraft.getInstance:()Lnet/minecraft/client/Minecraft;'
need '// net/minecraft/client/Minecraft.getOverlay:()Lnet/minecraft/client/gui/Overlay;'
need '// net/minecraft/network/chat/Component.literal:(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;'
need '// net/minecraft/network/chat/Component.empty:()Lnet/minecraft/network/chat/MutableComponent;'
need '// net/minecraft/world/InteractionResult.consumesAction:()Z'
need '// net/minecraft/client/player/LocalPlayer.getCooldowns:()Lnet/minecraft/world/item/ItemCooldowns;'
need '// net/minecraft/world/item/ItemCooldowns.isOnCooldown:(Lnet/minecraft/world/item/ItemStack;)Z'
need '// net/minecraft/client/multiplayer/ClientLevel.addParticle:(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V'
need '// net/minecraft/client/gui/components/Button.builder:(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;'
need 'addRenderableWidget:(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;'

# 2.5.x Legit-input ABI: these must be the real Minecraft/Fabric key-mapping shapes.
# ServerboundMovePlayerPacket.Rot is intentionally allowed for post-final-use rotation restoration;
# scripts/test-silent-use-preserves-movement.sh enforces that it never appears in the low-level use helper.
need '// net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.getBoundKeyOf:(Lnet/minecraft/client/KeyMapping;)Lcom/mojang/blaze3d/platform/InputConstants$Key;'
need '// net/minecraft/client/KeyMapping.click:(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V'
need '// net/minecraft/client/KeyMapping.isUnbound:()Z'
need '// net/minecraft/client/Options.keyHotbarSlots:[Lnet/minecraft/client/KeyMapping;'
need '// net/minecraft/client/Options.keySwapOffhand:Lnet/minecraft/client/KeyMapping;'
need '// net/minecraft/client/Options.keyUse:Lnet/minecraft/client/KeyMapping;'
need '// net/minecraft/client/player/LocalPlayer.getOffhandItem:()Lnet/minecraft/world/item/ItemStack;'
need '// net/minecraft/client/multiplayer/MultiPlayerGameMode.useItem:(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;'

forbid '// com/google/gson/Gson.toJson:(Ljava/lang/Object;Ljava/io/Writer;)V'
forbid 'ClientTickEvents.END_CLIENT_TICK:Lnet/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$Event;'
forbid 'ClientLevel.addParticle:(Lnet/minecraft/core/particles/DustParticleOptions;DDDDDD)V'
forbid 'Button.builder:(Lnet/minecraft/network/chat/Component;Ljava/util/function/Consumer;)'
forbid 'addRenderableWidget:(Ljava/lang/Object;)Ljava/lang/Object;'
echo 'PearlCatch manual ABI audit: PASS'
