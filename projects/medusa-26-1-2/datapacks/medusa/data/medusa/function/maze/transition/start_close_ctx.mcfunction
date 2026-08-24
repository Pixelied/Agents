$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=1,md_ne=0}] run scoreboard players set @s md_roll 1
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=1,md_ne=0}] at @s run function medusa:maze/wall/close_start
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=1,md_ns=0}] run scoreboard players set @s md_roll 2
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=1,md_ns=0}] at @s run function medusa:maze/wall/close_start
