$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=0,md_ne=1}] run scoreboard players set @s md_roll 1
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=0,md_ne=1}] at @s run function medusa:maze/wall/open_start
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=0,md_ns=1}] run scoreboard players set @s md_roll 2
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=0,md_ns=1}] at @s run function medusa:maze/wall/open_start
