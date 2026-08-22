scoreboard players add $maze_wait md_tmp 1
execute if score $maze_smoke_started md_tmp matches 0 if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=2},limit=1] run scoreboard players set $maze_smoke_started md_tmp 1
execute if score $maze_smoke_started md_tmp matches 1 if entity @e[type=minecraft:marker,tag=md.debug_maze_primary,scores={md_mphase=2},limit=1] run function medusa:debug/test_dungeon_progression
execute if score $maze_smoke_started md_tmp matches 0 if score $maze_wait md_tmp matches ..599 run schedule function medusa:debug/wait_for_maze_ready 1t replace
execute if score $maze_smoke_started md_tmp matches 0 as @e[type=minecraft:marker,tag=md.debug_maze_primary,limit=1] at @s if score $maze_wait md_tmp matches 600.. run function medusa:debug/maze_smoke/timeout_status
execute if score $maze_smoke_started md_tmp matches 0 if score $maze_wait md_tmp matches 600.. run say MEDUSA_MAZE_INITIAL_SOLVABLE_FAILED
