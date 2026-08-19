execute if block ~-28 ~-17 ~52 minecraft:stone_button[powered=true] if score @s md_p2_bl matches 0 run function medusa:puzzle/borrowed_gaze/press_left
execute if block ~-22 ~-17 ~52 minecraft:stone_button[powered=true] if score @s md_p2_br matches 0 run function medusa:puzzle/borrowed_gaze/press_right
execute if block ~-28 ~-17 ~52 minecraft:stone_button[powered=false] run scoreboard players set @s md_p2_bl 0
execute if block ~-22 ~-17 ~52 minecraft:stone_button[powered=false] run scoreboard players set @s md_p2_br 0
particle minecraft:end_rod ~-28 ~-14.5 ~49 0 0 0 0 1 force
particle minecraft:end_rod ~-25 ~-14.5 ~48 0 0 0 0 1 force
particle minecraft:end_rod ~-22 ~-14.5 ~49 0 0 0 0 1 force
execute if score @s md_p2_left matches 2 if score @s md_p2_right matches 1 run scoreboard players set @s md_p2_done 1
execute if score @s md_p2_done matches 1 if block ~-25 ~-17 ~55 minecraft:iron_bars run playsound minecraft:block.amethyst_block.resonate master @a[distance=..24] ~-25 ~-15 ~50 0.9 1.3
execute if score @s md_p2_done matches 1 if block ~-25 ~-17 ~55 minecraft:iron_bars run particle minecraft:end_rod ~-25 ~-14 ~50 2.0 1.2 2.0 0.02 32 force
execute if score @s md_p2_done matches 1 run fill ~-27 ~-17 ~55 ~-23 ~-13 ~55 minecraft:air
