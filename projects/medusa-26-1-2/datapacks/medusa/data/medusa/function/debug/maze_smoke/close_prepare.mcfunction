scoreboard players set $maze_debug_aborted md_tmp 0
kill @e[type=minecraft:marker,tag=md.maze.occupancy_probe]
tag @e[type=minecraft:marker,tag=md.maze.wall_controller,tag=!md.debug_occupied_controller,scores={md_mmode=2},limit=1,sort=nearest] add md.debug_occupied_controller
execute as @e[type=minecraft:marker,tag=md.debug_occupied_controller,limit=1] at @s run function medusa:debug/maze_smoke/spawn_occupancy_probe
execute unless entity @e[type=minecraft:marker,tag=md.debug_occupied_controller,limit=1] run say MEDUSA_MAZE_OCCUPIED_ABORT_FAILED
scoreboard players set $maze_close_wait md_tmp 0
schedule function medusa:debug/maze_smoke/close_tick 1t replace
