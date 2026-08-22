scoreboard players set @s md_tmp 0
$execute positioned ~ ~ ~-7 if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run scoreboard players set @s md_tmp 1
$execute positioned ~7 ~ ~ if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run scoreboard players set @s md_tmp 1
$execute positioned ~ ~ ~7 if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run scoreboard players set @s md_tmp 1
$execute positioned ~-7 ~ ~ if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run scoreboard players set @s md_tmp 1
execute if score @s md_tmp matches 0 run function medusa:maze/generate/backtrack with storage medusa:macro maze
execute if score @s md_tmp matches 1 store result score @s md_roll run random value 1..4
$execute if score @s md_tmp matches 1 if score @s md_roll matches 1 positioned ~ ~ ~-7 if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run function medusa:maze/generate/open_north with storage medusa:macro maze
$execute if score @s md_tmp matches 1 if score @s md_roll matches 2 positioned ~7 ~ ~ if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run function medusa:maze/generate/open_east with storage medusa:macro maze
$execute if score @s md_tmp matches 1 if score @s md_roll matches 3 positioned ~ ~ ~7 if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run function medusa:maze/generate/open_south with storage medusa:macro maze
$execute if score @s md_tmp matches 1 if score @s md_roll matches 4 positioned ~-7 ~ ~ if entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] run function medusa:maze/generate/open_west with storage medusa:macro maze
