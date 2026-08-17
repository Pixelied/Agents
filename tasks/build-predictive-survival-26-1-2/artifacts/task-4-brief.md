# Task 4 Brief — Mitigation, armor durability, absorption, and death protection

This task extends the pure simulator through the rest of the source-confirmed vanilla player damage path. Exact 26.1.2 source was re-checked before RED.

## Source-confirmed order after Task 3 hurt cooldown

1. If the source does not have `BYPASSES_ARMOR`, damage armor durability first, then calculate armor/toughness reduction using the post-break equipment state.
2. If `BYPASSES_EFFECTS`, skip both Resistance and enchantment protection.
3. Otherwise apply Resistance unless `BYPASSES_RESISTANCE` using `(amplifier + 1) * 20%` reduction, clamped by vanilla at zero damage.
4. If damage remains positive, apply enchantment protection unless `BYPASSES_ENCHANTMENTS`, clamped to 0..20 and using `damage * (1 - protection / 25)`.
5. Consume absorption before health.
6. Subtract remaining damage from health.
7. If health reaches `<= 0`, death protection may trigger unless the source has `BYPASSES_INVULNERABILITY`. Check main hand before offhand, consume one protection item, set health to 1, and apply its component effects.

`DAMAGES_HELMET` remains a special pre-cooldown stage: helmet durability is damaged first, then the same incoming hit receives the `0.75` helmet damage multiplier even if that durability damage broke the helmet. If the hit later reaches `hurtArmor`, surviving armor is damaged again as normal.

## Exact armor math

```text
toughnessFactor = 2 + toughness / 4
realArmor = clamp(armor - damage / toughnessFactor, armor * 0.2, 20)
armorFraction = realArmor / 25
modifiedArmorFraction = clamp(armorFraction * armorEffectivenessMultiplier, 0, 1)
postArmor = damage * (1 - modifiedArmorFraction)
```

The multiplier is a pure-snapshot representation of the weapon-driven `EnchantmentHelper.modifyArmorEffectiveness` result; Task 8 maps exact runtime state into it.

Armor durability damage is `(int) max(1, damage / 4)` and is based on damage before armor reduction.

## Source-driven correction to the original plan

The original one-durability armor-break regression was wrong for 26.1.2 because armor is damaged before armor reduction. A one-durability piece can break before it mitigates the first hit. The correct regression uses armor with 3 durability remaining and raw damage 8 (durability damage 2):
- hit 1 leaves 1 durability and still mitigates;
- after hurt cooldown expires, hit 2 breaks the piece before armor calculation and therefore causes greater post-armor/health damage.

## New/expanded pure snapshot types

- `ArmorPieceSnapshot` with slot, armor, toughness, enchantment protection, remaining durability, and `damageOnHurt`.
- `EffectInstanceSnapshot` with effect key, duration, and amplifier.
- `MitigationSnapshot` gains immutable armor-piece state while retaining the existing six-argument constructor for Task 3 compatibility.
- `StatusEffectsSnapshot` gains immutable effect payloads while retaining the existing two-argument constructor/accessors.
- `DeathProtectionSnapshot` represents optional main/offhand protection items and can consume main-hand first. A vanilla-totem fixture carries clear-all plus Regeneration II, Absorption II, and Fire Resistance I effects.

## RED tests

At minimum test:
- Resistance III: 10 post-armor -> 4 after Resistance.
- `BYPASSES_EFFECTS`: Resistance and protection both skipped.
- Protection 20: 10 -> 2 after magic protection.
- absorption 4 + health 10 + final incoming 6 -> absorption 0, health 8.
- ordinary armor math with armor 20/toughness 8/raw 10 -> post-armor 3.
- armor piece with 3 durability survives the first raw-8 hit but breaks before the second hit's armor calculation after cooldown expiry; second health damage is greater.
- `BYPASSES_ARMOR` neither reduces damage nor damages armor durability.
- main-hand and offhand death protection both work; main hand wins when both are present.
- `BYPASSES_INVULNERABILITY` prevents death protection.
- vanilla totem effects leave health at 1, grant Absorption II's immediate 8 absorption, and install the source-confirmed effect durations/amplifiers.

Use TDD: commit tests first and verify they fail because the new armor/effect/protection model is missing, then implement only this task's required stages and re-run full CI.
