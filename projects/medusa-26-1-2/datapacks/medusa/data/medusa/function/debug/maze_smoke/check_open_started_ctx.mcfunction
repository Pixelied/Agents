$execute if entity @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=1}] unless entity @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=2}] run say MEDUSA_MAZE_OPEN_FIRST_OK
$execute unless entity @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=1}] run say MEDUSA_MAZE_OPEN_FIRST_FAILED
$execute if entity @e[type=minecraft:block_display,tag=md.maze.wall_display,scores={md_eid=$(eid)}] run say MEDUSA_MAZE_WALL_DISPLAY_OK
$execute unless entity @e[type=minecraft:block_display,tag=md.maze.wall_display,scores={md_eid=$(eid)}] run say MEDUSA_MAZE_WALL_DISPLAY_FAILED
scoreboard players set $maze_collision md_tmp 0
$execute as @e[type=minecraft:marker,tag=md.maze.wall_controller,scores={md_eid=$(eid),md_mmode=1},limit=1] at @s run function medusa:debug/maze_smoke/check_collision
execute if score $maze_collision md_tmp matches 1 run say MEDUSA_MAZE_COLLISION_OK
execute unless score $maze_collision md_tmp matches 1 run say MEDUSA_MAZE_COLLISION_FAILED
execute if entity @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,scores={md_eid=900001,md_me=1,md_ne=1,md_ms=1,md_ns=1},limit=1] unless entity @e[tag=md.maze.wall_display,scores={md_eid=900001}] unless entity @e[tag=md.maze.wall_controller,scores={md_eid=900001}] run say MEDUSA_MAZE_INSTANCE_ISOLATION_OK
execute unless entity @e[type=minecraft:marker,tag=md.debug_maze_secondary_cell,scores={md_eid=900001,md_me=1,md_ne=1,md_ms=1,md_ns=1},limit=1] run say MEDUSA_MAZE_INSTANCE_ISOLATION_FAILED
