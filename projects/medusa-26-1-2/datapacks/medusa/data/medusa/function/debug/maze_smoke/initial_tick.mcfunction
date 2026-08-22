scoreboard players add $maze_initial_wait md_tmp 1
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 4 run function medusa:maze/validate/tick
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 5 run function medusa:debug/maze_smoke/initial_pass
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=3},limit=1] run say MEDUSA_MAZE_INITIAL_SOLVABLE_FAILED
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=4},limit=1] if score $maze_initial_wait md_tmp matches ..239 run schedule function medusa:debug/maze_smoke/initial_tick 1t replace
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=4},limit=1] if score $maze_initial_wait md_tmp matches 240.. run say MEDUSA_MAZE_INITIAL_SOLVABLE_FAILED
