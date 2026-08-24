execute if score @s md_morient matches 1 if block ~3 ~1 ~0 minecraft:barrier run scoreboard players set $maze_collision md_tmp 1
execute if score @s md_morient matches 2 if block ~0 ~1 ~3 minecraft:barrier run scoreboard players set $maze_collision md_tmp 1
