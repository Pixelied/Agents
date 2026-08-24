scoreboard players add $maze_proposal_wait md_tmp 1
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 3 run function medusa:maze/propose/mutate
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 4 run function medusa:maze/validate/tick
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score @s md_mphase matches 5 run function medusa:debug/maze_smoke/proposal_ready
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=3..4},limit=1] if score $maze_proposal_wait md_tmp matches ..799 run schedule function medusa:debug/maze_smoke/proposal_tick 1t replace
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=3..4},limit=1] if score $maze_proposal_wait md_tmp matches 800.. run say MEDUSA_MAZE_DELTA_FAILED
