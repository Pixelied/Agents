package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Map;
import java.util.Objects;

public final class NonTotemActionExecutor {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 40L;
    private static final double POSITION_TOLERANCE = 0.75d;
    private static final float VALUE_EPSILON = 0.001f;

    private Pending pending;

    public ExecutionStatus begin(SurvivalAction action, NonTotemExecutionContext context) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        pending = null;

        if (!action.legal() || !action.authoritativePrerequisitesSatisfied()) {
            return new ExecutionStatus.Failed("non-totem action is no longer legal", true);
        }

        if (action instanceof SurvivalAction.PlaceCover cover) {
            SurvivalAction.BlockTarget target = cover.target().orElse(null);
            if (target == null) return new ExecutionStatus.Failed("cover action has no executable block target", true);
            SurvivalAction.Hand hand = handHolding(context.base().inventory(), target.itemKey());
            if (hand == null) return missingHeldItem(target.itemKey());
            pending = new Pending(action, context.base().currentServerTick(), action.apply(context.player()));
            return new ExecutionStatus.WaitingForServer(
                "waiting for target cover block to be observed",
                new ExecutionCommand.PlaceBlock(target, hand)
            );
        }

        if (action instanceof SurvivalAction.SwapEquipment equipment) {
            String itemKey = singleEquipmentItem(equipment.equipmentUpdates());
            if (itemKey == null) {
                return new ExecutionStatus.Failed("equipment action must describe one concrete item swap", true);
            }
            SurvivalAction.Hand hand = handHolding(context.base().inventory(), itemKey);
            if (hand == null) return missingHeldItem(itemKey);
            if (equipmentSatisfied(equipment, context.player())) {
                return new ExecutionStatus.Confirmed("equipment state is already observed");
            }
            pending = new Pending(action, context.base().currentServerTick(), action.apply(context.player()));
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed equipment state",
                new ExecutionCommand.UseItem(hand)
            );
        }

        if (action instanceof SurvivalAction.ApplyEffects effects) {
            if (effects.itemKey().isBlank()) {
                return new ExecutionStatus.Failed("effect action has no executable item key", true);
            }
            SurvivalAction.Hand hand = handHolding(context.base().inventory(), effects.itemKey());
            if (hand == null) return missingHeldItem(effects.itemKey());
            if (effectsSatisfied(effects, context.player(), context.player())) {
                return new ExecutionStatus.Confirmed("effect state is already observed");
            }
            pending = new Pending(action, context.base().currentServerTick(), effects.apply(context.player()));
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed effect/health state",
                new ExecutionCommand.UseItem(hand)
            );
        }

        if (action instanceof SurvivalAction.PearlRescue pearl) {
            Vec3Snapshot target = pearl.targetPosition().orElse(null);
            if (target == null) return new ExecutionStatus.Failed("pearl rescue has no predicted teleport target", true);
            SurvivalAction.Hand hand = handHolding(context.base().inventory(), "minecraft:ender_pearl");
            if (hand == null) return missingHeldItem("minecraft:ender_pearl");
            if (near(context.player().position(), target)) {
                return new ExecutionStatus.Confirmed("pearl destination is already observed");
            }
            pending = new Pending(action, context.base().currentServerTick(), action.apply(context.player()));
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed pearl relocation",
                new ExecutionCommand.AimAndUseItem(hand, target)
            );
        }

        if (action instanceof SurvivalAction.Relocate relocate) {
            if (near(context.player().position(), relocate.targetPosition())) {
                return new ExecutionStatus.Confirmed("relocation target is already observed");
            }
            pending = new Pending(action, context.base().currentServerTick(), relocate.apply(context.player()));
            return new ExecutionStatus.WaitingForServer(
                "waiting for server-observed relocation",
                new ExecutionCommand.MoveToward(relocate.targetPosition())
            );
        }

        return new ExecutionStatus.Failed("unsupported non-totem action type", true);
    }

    public ExecutionStatus observe(NonTotemExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (pending == null) return new ExecutionStatus.Failed("no non-totem action is pending", true);

        if (context.base().currentServerTick() - pending.startedAtServerTick() > CONFIRMATION_TIMEOUT_TICKS) {
            pending = null;
            return new ExecutionStatus.Failed("server confirmation timed out", true);
        }

        SurvivalAction action = pending.action();
        boolean confirmed;
        if (action instanceof SurvivalAction.PlaceCover cover) {
            confirmed = cover.target().map(context.confirmedBlocks()::contains).orElse(false);
        } else if (action instanceof SurvivalAction.SwapEquipment equipment) {
            confirmed = equipmentSatisfied(equipment, context.player());
        } else if (action instanceof SurvivalAction.ApplyEffects effects) {
            confirmed = effectsSatisfied(effects, pending.expectedPlayer(), context.player());
        } else if (action instanceof SurvivalAction.PearlRescue pearl) {
            confirmed = pearl.targetPosition().map(target -> near(context.player().position(), target)).orElse(false);
        } else if (action instanceof SurvivalAction.Relocate relocate) {
            confirmed = near(context.player().position(), relocate.targetPosition());
        } else {
            pending = null;
            return new ExecutionStatus.Failed("pending action type became unsupported", true);
        }

        if (!confirmed) return new ExecutionStatus.WaitingForServer("waiting for authoritative action confirmation");
        pending = null;
        return new ExecutionStatus.Confirmed("non-totem action confirmed by observed state");
    }

    private static ExecutionStatus.Failed missingHeldItem(String itemKey) {
        return new ExecutionStatus.Failed("required item is not already in a server-recognized hand: " + itemKey, true);
    }

    private static SurvivalAction.Hand handHolding(InventorySnapshot inventory, String itemKey) {
        var selected = inventory.slot(inventory.selectedHotbarIndex());
        if (selected.isPresent() && selected.get().count() > 0 && selected.get().stackKey().equals(itemKey)) {
            return SurvivalAction.Hand.MAIN_HAND;
        }
        var offhand = inventory.slot(40);
        if (offhand.isPresent() && offhand.get().count() > 0 && offhand.get().stackKey().equals(itemKey)) {
            return SurvivalAction.Hand.OFF_HAND;
        }
        return null;
    }

    private static String singleEquipmentItem(Map<String, String> equipmentUpdates) {
        if (equipmentUpdates.size() != 1) return null;
        String item = equipmentUpdates.values().iterator().next();
        return item == null || item.isBlank() ? null : item;
    }

    private static boolean equipmentSatisfied(SurvivalAction.SwapEquipment action, PlayerSnapshot player) {
        for (Map.Entry<String, String> update : action.equipmentUpdates().entrySet()) {
            if (!update.getValue().equals(player.equipmentItemKeys().get(update.getKey()))) return false;
        }
        return !action.equipmentUpdates().isEmpty();
    }

    private static boolean effectsSatisfied(
        SurvivalAction.ApplyEffects action,
        PlayerSnapshot expected,
        PlayerSnapshot current
    ) {
        if (current.health() + VALUE_EPSILON < expected.health()) return false;
        if (current.absorption() + VALUE_EPSILON < expected.absorption()) return false;
        return statusEffectsContain(current.statusEffects(), action.statusEffectsAfter());
    }

    private static boolean statusEffectsContain(StatusEffectsSnapshot current, StatusEffectsSnapshot expected) {
        if (expected.fireResistance() && !current.fireResistance()) return false;
        if (expected.resistanceAmplifier() >= 0 && current.resistanceAmplifier() < expected.resistanceAmplifier()) return false;
        for (Map.Entry<String, EffectInstanceSnapshot> entry : expected.effects().entrySet()) {
            EffectInstanceSnapshot actual = current.effects().get(entry.getKey());
            if (actual == null || actual.amplifier() < entry.getValue().amplifier()) return false;
        }
        return true;
    }

    private static boolean near(Vec3Snapshot actual, Vec3Snapshot target) {
        double dx = actual.x() - target.x();
        double dy = actual.y() - target.y();
        double dz = actual.z() - target.z();
        return dx * dx + dy * dy + dz * dz <= POSITION_TOLERANCE * POSITION_TOLERANCE;
    }

    private record Pending(SurvivalAction action, long startedAtServerTick, PlayerSnapshot expectedPlayer) {
        private Pending {
            action = Objects.requireNonNull(action, "action");
            expectedPlayer = Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        }
    }
}
