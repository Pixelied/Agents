scoreboard players add $maze_close_wait md_tmp 1
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 7 run function medusa:maze/transition/close_tick
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 8 run function medusa:debug/maze_smoke/after_close
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=7},limit=1] if score $maze_close_wait md_tmp matches ..199 run schedule function medusa:debug/maze_smoke/close_tick 1t replace
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=7},limit=1] if score $maze_close_wait md_tmp matches 200.. run say MEDUSA_MAZE_OCCUPIED_ABORT_FAILED
