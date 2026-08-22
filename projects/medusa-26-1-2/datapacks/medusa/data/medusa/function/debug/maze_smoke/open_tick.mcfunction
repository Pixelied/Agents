scoreboard players add $maze_open_wait md_tmp 1
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 6 run function medusa:maze/transition/open_tick
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 7 run function medusa:debug/maze_smoke/close_prepare
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=6},limit=1] if score $maze_open_wait md_tmp matches ..159 run schedule function medusa:debug/maze_smoke/open_tick 1t replace
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=6},limit=1] if score $maze_open_wait md_tmp matches 160.. run say MEDUSA_MAZE_OPEN_FIRST_FAILED
