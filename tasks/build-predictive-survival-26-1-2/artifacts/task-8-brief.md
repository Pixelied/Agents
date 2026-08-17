# Task 8 Brief — Adapt live Minecraft 26.1.2 state into pure snapshots

Implement the first Minecraft-facing adapter layer while preserving the pure simulation boundary.

## Exact 26.1.2 source facts re-checked

- Damage tags live in `net.minecraft.tags.DamageTypeTags`; `DamageSource#is(TagKey<DamageType>)` is the runtime membership check.
- `LivingEntity#getItemBlockingWith()` is public and becomes non-null only after the current `BLOCKS_ATTACKS` component's `blockDelayTicks()` has elapsed.
- The standard shield's block delay is 5 ticks, but exact source/direction/type-specific blocked damage depends on `BlocksAttacks#resolveBlockedDamage` and must not be guessed globally.
- `LocalPlayer.lastHurt` is post-health-delta client state and must never seed the server raw `lastHurt`; capture `HurtState.unknown()` here and let `ServerHurtStateTracker` own server-shadow state.
- `EnchantmentHelper#getDamageProtection` requires `ServerLevel`; the client adapter must not call it with a `ClientLevel` or fabricate an exact value. Use conservative zero source-specific protection until a later source-aware client calculation is runtime-proven.
- Death protection is a `DataComponents.DEATH_PROTECTION` component and may exist in either hand. Vanilla totem component equality can map to the source-confirmed totem payload; unknown custom components remain generic protection rather than fake exact effects.

## Interfaces

```java
public final class MinecraftSnapshotFactory {
    public PlayerSnapshot capture(net.minecraft.client.player.LocalPlayer player);
}
public final class MinecraftDamageAdapter {
    public DamageSourceSnapshot snapshot(net.minecraft.world.damagesource.DamageSource source,
                                         float rawDamage,
                                         net.minecraft.client.player.LocalPlayer player);
    public static TagKey<DamageType> tagFor(DamageFlag flag);
}
public final class MinecraftBlockingAdapter {
    public BlockingSnapshot capture(net.minecraft.client.player.LocalPlayer player);
    public static BlockingSnapshot snapshot(boolean using, int elapsed, int required, float guaranteedBlockedFraction);
}
public final class MinecraftEquipmentAdapter {
    public MitigationSnapshot mitigation(net.minecraft.client.player.LocalPlayer player);
    public StatusEffectsSnapshot effects(net.minecraft.client.player.LocalPlayer player);
    public DeathProtectionSnapshot deathProtection(net.minecraft.client.player.LocalPlayer player);
}
```

## Safety behavior

- Runtime blocking snapshot represents readiness; only a guaranteed unconditional blocked fraction may be credited without a source/direction. Standard source-specific blocking decisions can be refined later by the planner/adapters.
- Aggregate armor/toughness uses live player attributes. Per-piece vanilla add-value armor/toughness modifiers are captured so later breakage can update the snapshot; unsupported non-additive custom contributions must not be guessed.
- Source-specific enchantment protection remains zero in this generic capture path.
- No mixin should be added unless public 26.1.2 APIs prove insufficient.

## RED tests

- `MinecraftDamageAdapter.tagFor(BYPASSES_ARMOR)` equals `DamageTypeTags.BYPASSES_ARMOR`.
- Every current `DamageFlag` has an explicit runtime tag mapping.
- blocking elapsed 4 / required 5 is inactive; elapsed 5 / required 5 is active.
- adapter helper rejects a blocked fraction outside 0..1.

After GREEN, `compileClientJava` is part of verification so API mistakes against actual 26.1.2 names are caught even when unit tests use helper paths.
