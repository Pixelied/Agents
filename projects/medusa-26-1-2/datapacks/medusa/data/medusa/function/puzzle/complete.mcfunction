execute if score @s md_p1_done matches 1 if score @s md_p2_done matches 1 if score @s md_p3_done matches 1 unless score @s md_dungeon_clear matches 1 run scoreboard players set @s md_dungeon_clear 1
execute if score @s md_dungeon_clear matches 1 if block ~43 ~-17 ~66 minecraft:iron_bars run playsound minecraft:block.beacon.activate master @a[distance=..32] ~43 ~-15 ~66 1.0 0.75
execute if score @s md_dungeon_clear matches 1 run fill ~43 ~-17 ~64 ~43 ~-12 ~68 minecraft:air
