# The generated dungeon must begin sealed at every progression boundary.
execute unless block ~3 ~-17 ~41 minecraft:iron_bars run say MEDUSA_P1_GATE_FAILED
execute unless block ~-25 ~-17 ~55 minecraft:iron_bars run say MEDUSA_P2_GATE_FAILED
execute unless block ~-2 ~-17 ~62 minecraft:iron_bars run say MEDUSA_P3_GATE_FAILED
execute unless block ~43 ~-17 ~66 minecraft:iron_bars run say MEDUSA_FINAL_GATE_FAILED
# The Golden Gorgon Eye must exist before the trials are completed.
execute positioned ~64 ~-16 ~66 if entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..2,limit=1] run say MEDUSA_EYE_PRESENT_OK
execute positioned ~64 ~-16 ~66 unless entity @e[type=minecraft:interaction,tag=md.pedestal_interaction,distance=..2,limit=1] run say MEDUSA_EYE_PRESENT_FAILED
# Solve Averted Eyes with its intended orientation sequence.
scoreboard players set @s md_p1_o1 1
scoreboard players set @s md_p1_o2 3
scoreboard players set @s md_p1_o3 2
function medusa:puzzle/averted_eyes/submit
execute if score @s md_p1_done matches 1 if block ~3 ~-17 ~41 minecraft:air run say MEDUSA_P1_GATE_OK
execute unless score @s md_p1_done matches 1 run say MEDUSA_P1_GATE_FAILED
execute unless block ~3 ~-17 ~41 minecraft:air run say MEDUSA_P1_GATE_FAILED
# Solve Borrowed Gaze with the intended left/right mirror alignment.
scoreboard players set @s md_p2_left 2
scoreboard players set @s md_p2_right 1
function medusa:puzzle/borrowed_gaze/tick
execute if score @s md_p2_done matches 1 if block ~-25 ~-17 ~55 minecraft:air run say MEDUSA_P2_GATE_OK
execute unless score @s md_p2_done matches 1 run say MEDUSA_P2_GATE_FAILED
execute unless block ~-25 ~-17 ~55 minecraft:air run say MEDUSA_P2_GATE_FAILED
# Simulate reaching the safe destination of Blind Passage, then let its own tick open the sanctum gate.
scoreboard players set @s md_p3_done 1
function medusa:puzzle/blind_passage/tick
execute if block ~-2 ~-17 ~62 minecraft:air run say MEDUSA_P3_GATE_OK
execute unless block ~-2 ~-17 ~62 minecraft:air run say MEDUSA_P3_GATE_FAILED
# All three flags must release the final arena seal.
function medusa:puzzle/complete
execute if score @s md_dungeon_clear matches 1 if block ~43 ~-17 ~66 minecraft:air run say MEDUSA_FINAL_GATE_OK
execute unless score @s md_dungeon_clear matches 1 run say MEDUSA_FINAL_GATE_FAILED
execute unless block ~43 ~-17 ~66 minecraft:air run say MEDUSA_FINAL_GATE_FAILED
