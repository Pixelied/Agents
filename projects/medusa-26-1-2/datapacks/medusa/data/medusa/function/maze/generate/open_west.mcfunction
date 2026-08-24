scoreboard players set @s md_mw 1
$execute positioned ~-7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run scoreboard players set @s md_me 1
$execute positioned ~-7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run scoreboard players set @s md_mseen 1
$execute positioned ~-7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=1},distance=..1,limit=1] run scoreboard players set @s md_mparent 2
tag @s remove md.maze.cursor
$execute positioned ~-7 ~ ~ run tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=1},distance=..1,limit=1] add md.maze.cursor
$scoreboard players add @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_count 1
