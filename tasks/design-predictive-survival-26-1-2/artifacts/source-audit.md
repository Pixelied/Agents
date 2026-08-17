# Minecraft 26.1.2 survival-engine source audit

This is a compact behavioral audit of the user-supplied decompiled Minecraft 26.1.2 source used during design. The complete source archive is intentionally **not** copied into the Agents repository.

Source archive SHA-256: `3289004a631f4f8327b621bb7ad5793d9d39622568771fdd024a3756417a8f84`

The design session inspected the extracted source under `/mnt/data/mcsrc2612/src/main/java`. Future agents must not depend on that session-local path; regenerate/attach exact Minecraft 26.1.2 sources through the pinned Fabric Loom project and re-check these methods before changing formulas.

## Confirmed findings

1. **Player preprocessing — `Player#hurtServer`**: player/gamerule invulnerability is checked first; ability invulnerability is bypassed only by `BYPASSES_INVULNERABILITY`; difficulty-scaled damage uses Peaceful zero, Easy `min(damage / 2 + 1, damage)`, Hard `damage * 1.5`.

2. **Living damage ordering — `LivingEntity#hurtServer`**: Fire Resistance rejects `IS_FIRE` damage early; item blocking occurs before freezing/helmet handling and hurt cooldown; freezing extra-damage and `DAMAGES_HELMET` reduction occur before `lastHurt`; armor, Resistance, enchantment protection, absorption, and health loss occur later in `actuallyHurt`.

3. **Hurt cooldown / `lastHurt`**: during `invulnerableTime > 10` and unless the source has `BYPASSES_COOLDOWN`, damage `<= lastHurt` is rejected; a larger hit processes only `newDamage - lastHurt`, then replaces `lastHurt` with the new pre-armor value. Outside that window, full damage is processed and `invulnerableTime` becomes 20. The inspected vanilla generated tag provider does not populate `BYPASSES_COOLDOWN`, but runtime tags must still be honored.

4. **Client/server hurt-state mismatch**: `LocalPlayer#hurtTo` sets client `lastHurt` from actual health delta, while server `LivingEntity#hurtServer` stores it before armor/effects/enchantments/absorption. Client `lastHurt` is therefore not an exact server raw-damage value; a conservative shadow state is required.

5. **Armor/effects — `getDamageAfterArmorAbsorb`, `getDamageAfterMagicAbsorb`, `CombatRules`**: armor uses armor value, toughness, and weapon-driven `EnchantmentHelper.modifyArmorEffectiveness`; `BYPASSES_EFFECTS` skips the magic/effect stage; Resistance reduces 20% per level unless bypassed; enchantment protection is skipped by `BYPASSES_ENCHANTMENTS` and otherwise uses the vanilla 0–20 protection calculation.

6. **Death protection — `LivingEntity#checkTotemDeathProtection`**: `BYPASSES_INVULNERABILITY` cannot trigger death protection; vanilla checks `DEATH_PROTECTION` in both interaction hands; success consumes one item, sets health to 1, applies component effects, and broadcasts the pop.

7. **Shield timing — `Items.SHIELD`, `BlocksAttacks`, `getItemBlockingWith`**: the standard shield has `block_delay_seconds = 0.25`; `blockDelayTicks()` yields 5 ticks; the item is not considered blocking before that use-time threshold.

8. **Shield applicability — `LivingEntity#applyItemBlocking`**: blocking respects component bypass types and horizontal angle; piercing arrows bypass this blocking path; a fully blocked hit can reduce processed damage to zero, but it does not establish a useful nonzero `lastHurt` against a later larger hit.

9. **Explosion exposure/damage — `ServerExplosion`, `ExplosionDamageCalculator`**: exposure samples the entity AABB with block-collider raycasts to the center; raw entity damage uses the 26.1.2 distance/exposure formula; `explode()` calls `hurtEntities()` before `interactWithBlocks(...)`, so a server-accepted occluding placement can reduce exposure even if the explosion destroys that block afterward.

10. **Ender pearl — `ThrownEnderpearl#onHit`**: successful player teleport resets fall distance/current impulse context and then applies 5 raw `ENDER_PEARL` damage.

11. **Wind-charge fall handling — `ServerPlayer#onExplosionHit`, `LivingEntity#causeFallDamage`**: a wind-charge explosion establishes impulse context; later effective fall distance can be limited relative to the impulse impact position with post-impulse grace state.

12. **Mace fall reset — `MaceItem`**: a valid smash establishes impulse/fall handling in `hurtEnemy`; `postHurtEnemy` resets the attacker's fall distance after a valid smash.

13. **Inventory emergency swap — `AbstractContainerMenu#doClick`**: `ContainerInput.SWAP` accepts buttons 0–8 for hotbar and 40 for offhand, enabling a one-container-click inventory-to-hotbar/offhand swap when the source slot is mapped correctly.

14. **Selected hotbar packet — `ServerGamePacketListenerImpl#handleSetCarriedItem`**: selected slots 0–8 are accepted; changing selected slot stops item use only when the actively used hand is `MAIN_HAND`, so an active offhand shield can in principle remain active while main hand changes.

15. **Container state-id behavior — `ServerGamePacketListenerImpl#handleContainerClick`**: when container id/menu/slot are valid, the server still performs the click if packet `stateId` differs; mismatch causes a full resync afterward rather than automatic click rejection. Transaction code must reconcile the resync and distinguish it from an actually ignored invalid-menu action.

16. **Notable generated damage tags — `DamageTypeTagsProvider`**: `BYPASSES_INVULNERABILITY` and `BYPASSES_RESISTANCE` include `FELL_OUT_OF_WORLD` and `GENERIC_KILL`; `BYPASSES_EFFECTS` includes `STARVE`; `BYPASSES_ENCHANTMENTS` includes `SONIC_BOOM`; `BYPASSES_SHIELD` includes `BYPASSES_ARMOR` plus additional environmental/contact sources. Runtime tags remain authoritative.

17. **Freeze-immunity equipment — `LivingEntity#canFreeze`, `VanillaItemTagsProvider`**: wearing an item in `ItemTags.FREEZE_IMMUNE_WEARABLES` prevents freezing; the inspected vanilla tag contains the leather armor pieces (plus leather horse armor for applicable entities).

## Implementation rule

If future runtime/source evidence conflicts with this audit, re-open exact 26.1.2 behavior and update the design/tests from evidence. Do not preserve this audit merely because it was written first.
