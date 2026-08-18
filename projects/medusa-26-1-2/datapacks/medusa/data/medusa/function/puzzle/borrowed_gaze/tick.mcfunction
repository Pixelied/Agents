execute if block ~-25 ~-17 ~45 minecraft:stone_button[powered=true] if score @s md_p2_bl matches 0 run function medusa:puzzle/borrowed_gaze/press_left
execute if block ~-24 ~-17 ~45 minecraft:stone_button[powered=true] if score @s md_p2_br matches 0 run function medusa:puzzle/borrowed_gaze/press_right
execute if block ~-25 ~-17 ~45 minecraft:stone_button[powered=false] run scoreboard players set @s md_p2_bl 0
execute if block ~-24 ~-17 ~45 minecraft:stone_button[powered=false] run scoreboard players set @s md_p2_br 0
particle minecraft:end_rod ~-26 ~-15.5 ~45 0 0 0 0 1 force
particle minecraft:end_rod ~-24.5 ~-15.5 ~45 0 0 0 0 1 force
particle minecraft:end_rod ~-23 ~-15.5 ~45 0 0 0 0 1 force
execute if score @s md_p2_left matches 2 if score @s md_p2_right matches 1 run scoreboard players set @s md_p2_done 1
execute if score @s md_p2_done matches 1 run playsound minecraft:block.amethyst_block.resonate master @a[distance=..18] ~-24 ~-16 ~45 0.8 1.3
