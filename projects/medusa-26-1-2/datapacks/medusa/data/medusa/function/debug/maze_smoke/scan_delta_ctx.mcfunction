$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] if score @s md_me matches 0 if score @s md_ne matches 1 run scoreboard players set $maze_has_open md_tmp 1
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] if score @s md_ms matches 0 if score @s md_ns matches 1 run scoreboard players set $maze_has_open md_tmp 1
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] if score @s md_me matches 1 if score @s md_ne matches 0 run scoreboard players set $maze_has_close md_tmp 1
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] if score @s md_ms matches 1 if score @s md_ns matches 0 run scoreboard players set $maze_has_close md_tmp 1
