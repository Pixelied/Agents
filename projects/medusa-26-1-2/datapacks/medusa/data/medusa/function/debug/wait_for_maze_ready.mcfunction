scoreboard players add $maze_wait md_tmp 1
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=2},limit=1] run function medusa:debug/test_dungeon_progression
execute unless entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=2},limit=1] if score $maze_wait md_tmp matches ..599 run schedule function medusa:debug/wait_for_maze_ready 1t replace
execute as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s unless score @s md_mphase matches 2 if score $maze_wait md_tmp matches 600.. run function medusa:debug/maze_smoke/timeout_status
execute unless entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=2},limit=1] if score $maze_wait md_tmp matches 600.. run say MEDUSA_MAZE_INITIAL_SOLVABLE_FAILED
