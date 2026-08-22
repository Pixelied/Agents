execute if score @s md_morient matches 1 run say MEDUSA_MAZE_DEBUG_OCCUPIED_ORIENT_EAST
execute if score @s md_morient matches 2 run say MEDUSA_MAZE_DEBUG_OCCUPIED_ORIENT_SOUTH
execute if entity @e[type=minecraft:marker,tag=md.maze.occupancy_probe,distance=..5,limit=1] run say MEDUSA_MAZE_DEBUG_OCCUPANCY_PROBE_NEAR
execute unless entity @e[type=minecraft:marker,tag=md.maze.occupancy_probe,distance=..5,limit=1] run say MEDUSA_MAZE_DEBUG_OCCUPANCY_PROBE_NOT_NEAR
function medusa:maze/wall/check_occupied
execute if score @s md_tmp matches 1 run say MEDUSA_MAZE_DEBUG_OCCUPANCY_DETECTED
execute unless score @s md_tmp matches 1 run say MEDUSA_MAZE_DEBUG_OCCUPANCY_NOT_DETECTED
