package dev.pixelied.maceswap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Inventory transactions consist only of vanilla atomic hotbar SWAP clicks:
 * - hotbar chestplate: armor <-> hotbar
 * - main inventory chestplate: main <-> reserved hotbar; armor <-> reserved hotbar
 * Reverse the exact same operations to recover both original locations.
 * No cursor clicks and no synthetic attack/movement input are ever sent.
 */
public final class SwapController {
    private static final int CHEST_SLOT = 6; // 26.3 InventoryMenu helmet=5, chest=6
    private enum Phase { IDLE, WAIT_BUFFER, WAIT_ARMOR, ARMED, RESTORE_ARMOR, RESTORE_SOURCE, REGLIDE, COOLDOWN, SUSPENDED }
    private final Minecraft minecraft;
    private final PredictionEngine prediction = new PredictionEngine();
    private SwapConfig config;
    private Phase phase = Phase.IDLE;
    private int tick;
    private int phaseStart;
    private int lastPacketTick;
    private int reglideAttempts;
    private int targetId = -1;
    private int bufferSlot = -1;
    private int originalSlot = -1;
    private boolean needsBuffer;
    private ItemStack chestplate = ItemStack.EMPTY;
    private ItemStack elytra = ItemStack.EMPTY;
    private ItemStack originalBuffer = ItemStack.EMPTY;
    private boolean previousAttack;
    private boolean queuedAttackAttempt;

    public void recordAttackAttempt() { queuedAttackAttempt = true; }
    private boolean announcedSuspension;
    private boolean restoreArmorSent;
    private boolean restoreSourceSent;
    private String status = "Idle";

    public SwapController(Minecraft minecraft, SwapConfig config) {
        this.minecraft = minecraft;
        this.config = config;
    }

    public void setConfig(SwapConfig updated) {
        this.config = updated;
        if (!config.enabled && phase != Phase.IDLE && phase != Phase.COOLDOWN && phase != Phase.SUSPENDED) beginRecovery("Disabled");
    }

    public String status() { return status; }
    public String phase() { return phase.name(); }

    public void onDisconnect() {
        // Local state is not transferable across servers or a changed player instance.
        reset();
        prediction.clear();
    }

    private void reset() {
        phase = Phase.IDLE;
        targetId = bufferSlot = originalSlot = -1;
        needsBuffer = false;
        elytra = chestplate = originalBuffer = ItemStack.EMPTY;
        previousAttack = false;
        queuedAttackAttempt = false;
        status = "Idle";
        announcedSuspension = false;
        restoreArmorSent = false;
        restoreSourceSent = false;
        reglideAttempts = 0;
    }

    public void tick() {
        tick++;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            reset();
            return;
        }
        boolean attack = minecraft.options.keyAttack.isDown();
        boolean newAttack = (attack && !previousAttack) || queuedAttackAttempt;
        queuedAttackAttempt = false;
        previousAttack = attack;
        if (!config.enabled && phase == Phase.IDLE) return;
        prediction.observe(minecraft, config);
        if (config.debugOverlay && tick % 20 == 0) {
            player.sendOverlayMessage(Component.literal("Mace Swap | " + phase + " | " + status));
        }
        if (phase == Phase.COOLDOWN) {
            if (tick - phaseStart >= config.cooldownTicks) reset();
            return;
        }
        if (phase == Phase.IDLE) {
            if (config.enabled) attemptStart(player);
            return;
        }
        if (phase == Phase.SUSPENDED) return; // Never guess where equipment went.
        if (!player.isAlive()) {
            suspend("Player died before equipment was safely restored");
            return;
        }
        // Don't move slots under an inventory GUI or other container; recover on return.
        if (minecraft.gui.screen() != null || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
            if (phase != Phase.RESTORE_ARMOR && phase != Phase.RESTORE_SOURCE && phase != Phase.REGLIDE) {
                status = "Interrupted by screen or inventory action";
            }
            return;
        }
        if (phase == Phase.WAIT_BUFFER) {
            if (tick - lastPacketTick < 1) return;
            if (equipmentMatches(player.getInventory().getItem(bufferSlot), chestplate)
                && equipmentMatches(player.getInventory().getItem(originalSlot), originalBuffer)) {
                performSwap(player, CHEST_SLOT, bufferSlot);
                phase = Phase.WAIT_ARMOR;
                status = "Waiting for chestplate swap";
                phaseStart = tick;
                return;
            }
            if (timedOut(player)) beginRecovery("Buffer swap failed");
            return;
        }
        if (phase == Phase.WAIT_ARMOR) {
            if (equipmentMatches(player.getItemBySlot(EquipmentSlot.CHEST), chestplate)
                && equipmentMatches(player.getInventory().getItem(bufferSlot), elytra)) {
                phase = Phase.ARMED;
                phaseStart = tick;
                status = "Smash opportunity armed";
                return;
            }
            if (timedOut(player)) beginRecovery("Chestplate swap failed");
            return;
        }
        if (phase == Phase.ARMED) {
            // Manual hit OR miss. No automatic attack is attempted.
            if (newAttack && tick - phaseStart >= 1) {
                beginRecovery("Attack input detected");
            } else if (tick - phaseStart >= config.opportunityTicks + latencyTicks(player) + 7) {
                beginRecovery("Opportunity expired");
            } else if (player.onGround() || !player.getMainHandItem().is(Items.MACE)) {
                beginRecovery("Glide or mace interrupted");
            } else if (targetId >= 0 && (minecraft.level.getEntity(targetId) == null ||
                minecraft.level.getEntity(targetId).distanceToSqr(player) > Math.pow(config.searchRadius * 1.6, 2))) {
                beginRecovery("Target left prediction radius");
            }
            return;
        }
        if (phase == Phase.RESTORE_ARMOR) {
            if (!equipmentMatches(player.getItemBySlot(EquipmentSlot.CHEST), elytra)) {
                if (equipmentMatches(player.getItemBySlot(EquipmentSlot.CHEST), chestplate)
                    && equipmentMatches(player.getInventory().getItem(bufferSlot), elytra)) {
                    if (restoreArmorSent || tick - lastPacketTick < 1) return;
                    performSwap(player, CHEST_SLOT, bufferSlot);
                    restoreArmorSent = true;
                    return;
                }
                // A click might still be in flight. Do not repeat a click (it would swap back!).
                if (timedOut(player)) suspend("Equipment changed unexpectedly; manual check required");
                return;
            }
            // Elytra locally observed in armor slot. Restore temporary buffer contents next.
            if (needsBuffer) {
                phase = Phase.RESTORE_SOURCE;
                phaseStart = tick;
                status = "Restoring original inventory positions";
            } else {
                phase = Phase.REGLIDE;
                phaseStart = tick;
            }
            attemptReglide(player);
            return;
        }
        if (phase == Phase.RESTORE_SOURCE) {
            attemptReglide(player);
            if (equipmentMatches(player.getInventory().getItem(originalSlot), chestplate)
                && equipmentMatches(player.getInventory().getItem(bufferSlot), originalBuffer)) {
                phase = Phase.REGLIDE;
                phaseStart = tick;
                status = "Inventory restored";
                return;
            }
            if (equipmentMatches(player.getInventory().getItem(bufferSlot), chestplate)
                && equipmentMatches(player.getInventory().getItem(originalSlot), originalBuffer)) {
                if (!restoreSourceSent && tick - lastPacketTick >= 2) {
                    performSwap(player, menuSlot(originalSlot), bufferSlot);
                    restoreSourceSent = true;
                } else if (restoreSourceSent && timedOut(player)) {
                    suspend("Inventory source swap not confirmed; manual check required");
                }
            } else if (timedOut(player)) {
                suspend("Buffer contents changed; manual inventory check required");
            }
            return;
        }
        if (phase == Phase.REGLIDE) {
            if (!equipmentMatches(player.getItemBySlot(EquipmentSlot.CHEST), elytra)) {
                suspend("Elytra no longer equipped during recovery");
                return;
            }
            if (player.onGround() || !config.automaticRecovery) {
                finish("Elytra restored");
                return;
            }
            if (player.isFallFlying()) {
                if (tick - phaseStart >= latencyTicks(player) + 4) finish("Elytra restored; glide requested");
                return;
            }
            attemptReglide(player);
            if (tick - phaseStart > Math.max(24, config.recoveryRetries * 3 + latencyTicks(player) * 2)) {
                finish("Elytra restored; glide could not be confirmed");
            }
        }
    }

    private void attemptStart(LocalPlayer player) {
        if (minecraft.gui.screen() != null || player.containerMenu != player.inventoryMenu || player.isSpectator()
            || player.getAbilities().flying || player.onGround() || !player.isFallFlying()
            || !player.getMainHandItem().is(Items.MACE) || player.isPassenger()
            || !player.inventoryMenu.getCarried().isEmpty()) return;
        ItemStack currentChest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!currentChest.is(Items.ELYTRA) || !net.minecraft.world.entity.LivingEntity.canGlideUsing(currentChest, EquipmentSlot.CHEST)) return;
        int chosen = findChestplate(player);
        if (chosen < 0) { status = "Put a chestplate in your inventory"; return; }
        int selected = player.getInventory().getSelectedSlot();
        originalSlot = chosen;
        needsBuffer = chosen >= 9;
        bufferSlot = needsBuffer ? findSpareHotbar(player, selected) : chosen;
        if (bufferSlot < 0) return;
        int latency = latencyTicks(player);
        int exitTick = latency + (needsBuffer ? 2 : 1) + config.extraLeadTicks;
        PredictionEngine.Forecast forecast = prediction.forecast(minecraft, config, exitTick);
        if (forecast == null || forecast.smashTick() <= exitTick) return;
        targetId = forecast.targetId();
        chestplate = player.getInventory().getItem(chosen).copy();
        elytra = currentChest.copy();
        originalBuffer = player.getInventory().getItem(bufferSlot).copy();
        status = "Target #" + targetId + " in ~" + forecast.smashTick() + "t";
        phaseStart = tick;
        reglideAttempts = 0;
        if (needsBuffer) {
            performSwap(player, menuSlot(chosen), bufferSlot);
            phase = Phase.WAIT_BUFFER;
        } else {
            performSwap(player, CHEST_SLOT, bufferSlot);
            phase = Phase.WAIT_ARMOR;
        }
    }

    private int findChestplate(LocalPlayer player) {
        int hotbar = -1, main = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.has(DataComponents.GLIDER) && player.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST
                && player.isEquippableInSlot(stack, EquipmentSlot.CHEST)) {
                if (i < 9 && hotbar < 0) hotbar = i;
                else if (i >= 9 && main < 0) main = i;
            }
        }
        return hotbar >= 0 ? hotbar : main;
    }

    private int findSpareHotbar(LocalPlayer player, int selected) {
        for (int i = 0; i < 9; i++) if (i != selected && player.getInventory().getItem(i).isEmpty()) return i;
        for (int i = 0; i < 9; i++) if (i != selected) return i;
        return -1;
    }

    private static int menuSlot(int inventoryIndex) { return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex; }

    private void performSwap(LocalPlayer player, int menuSlot, int hotbarSlot) {
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) return;
        minecraft.gameMode.handleContainerInput(0, menuSlot, hotbarSlot, ContainerInput.SWAP, player);
        lastPacketTick = tick;
    }

    private static boolean equipmentMatches(ItemStack actual, ItemStack expected) {
        if (actual.isEmpty() || expected.isEmpty()) return actual.isEmpty() && expected.isEmpty();
        if (actual.getItem() != expected.getItem() || actual.getCount() != expected.getCount()) return false;
        // Durability may change during an attack. Preserve all other components in identity checks.
        ItemStack a = actual.copy();
        ItemStack b = expected.copy();
        a.remove(DataComponents.DAMAGE);
        b.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private int latencyTicks(LocalPlayer player) {
        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        int latency = info == null ? config.latencyFloorMs : info.getLatency();
        return TrajectoryMath.latencyTicks(latency, config.latencyFloorMs);
    }

    private boolean timedOut(LocalPlayer player) {
        return tick - phaseStart > Math.max(8, latencyTicks(player) * 3 + 4);
    }

    private void beginRecovery(String reason) {
        if (phase == Phase.RESTORE_ARMOR || phase == Phase.RESTORE_SOURCE || phase == Phase.REGLIDE || phase == Phase.SUSPENDED) return;
        phase = Phase.RESTORE_ARMOR;
        restoreArmorSent = false;
        restoreSourceSent = false;
        phaseStart = tick;
        status = reason + " - restoring";
    }

    private void attemptReglide(LocalPlayer player) {
        if (!config.automaticRecovery || reglideAttempts >= config.recoveryRetries) return;
        if (!equipmentMatches(player.getItemBySlot(EquipmentSlot.CHEST), elytra)) return;
        if (player.isFallFlying() || player.onGround() || player.isInWater() || player.isPassenger()) return;
        if (tick - lastPacketTick < 2 || tick % 2 != 0) return;
        if (player.tryToStartFallFlying()) {
            // Same action and local state transition produced by pressing Space in LocalPlayer#aiStep.
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            reglideAttempts++;
            status = "Requesting Elytra glide (" + reglideAttempts + ")";
        }
    }

    private void finish(String message) {
        phase = Phase.COOLDOWN;
        phaseStart = tick;
        status = message;
    }

    private void suspend(String message) {
        phase = Phase.SUSPENDED;
        status = message;
        if (!announcedSuspension && minecraft.player != null) {
            announcedSuspension = true;
            minecraft.player.sendSystemMessage(Component.literal("[Mace Swap] " + message));
        }
    }
}
