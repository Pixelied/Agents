scoreboard players set @s md_count 0
$execute as @e[type=minecraft:marker,tag=md.maze.trap,scores={md_eid=$(eid)}] run scoreboard players add @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_count 1
scoreboard players set @s md_tmp 0
$execute as @e[type=minecraft:marker,tag=md.maze.trap,scores={md_eid=$(eid),md_marmed=1}] run scoreboard players add @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_tmp 1
execute if score @s md_count matches 7 if score @s md_tmp matches 4 run say MEDUSA_MAZE_TRAPS_OK
execute unless score @s md_count matches 7 run say MEDUSA_MAZE_TRAPS_FAILED
execute unless score @s md_tmp matches 4 run say MEDUSA_MAZE_TRAPS_FAILED
