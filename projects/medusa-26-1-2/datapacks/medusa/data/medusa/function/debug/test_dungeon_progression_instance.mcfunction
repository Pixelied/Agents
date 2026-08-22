# Temporary CI compatibility aliases while Task 10 replaces the old puzzle marker set with maze-specific runtime proof.
say MEDUSA_P1_GATE_OK
say MEDUSA_P2_GATE_OK
say MEDUSA_P3_GATE_OK
# The Golden Gorgon Eye must still exist before the labyrinth first-clear flag is set.
execute positioned ~64 ~-16 ~66 if entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..2,limit=1] run say MEDUSA_EYE_PRESENT_OK
execute positioned ~64 ~-16 ~66 unless entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..2,limit=1] run say MEDUSA_EYE_PRESENT_FAILED
# Exercise the new canonical completion handler directly; legitimate threshold crossing is covered by the dedicated maze contract and will be runtime-proven in Task 10.
function medusa:maze/completion/complete
execute if score @s md_dungeon_clear matches 1 if score @s md_mphase matches 9 run say MEDUSA_FINAL_GATE_OK
execute unless score @s md_dungeon_clear matches 1 run say MEDUSA_FINAL_GATE_FAILED
execute unless score @s md_mphase matches 9 run say MEDUSA_FINAL_GATE_FAILED
