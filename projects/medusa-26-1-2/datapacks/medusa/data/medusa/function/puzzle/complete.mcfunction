execute if score @s md_p1_done matches 1 if score @s md_p2_done matches 1 if score @s md_p3_done matches 1 unless score @s md_dungeon_clear matches 1 run scoreboard players set @s md_dungeon_clear 1
execute if score @s md_dungeon_clear matches 1 run fill ~38 ~-17 ~68 ~41 ~-15 ~71 minecraft:air
