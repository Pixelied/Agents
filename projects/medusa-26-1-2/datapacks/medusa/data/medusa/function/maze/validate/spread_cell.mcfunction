tag @s add md.maze.spread_source
scoreboard players set @s md_mfront 0
# Child parent codes: north child->south=3, east child->west=4, south child->north=1, west child->east=2.
scoreboard players set @s md_roll 3
$execute if score @s md_nn matches 1 positioned ~ ~ ~-7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
scoreboard players set @s md_roll 4
$execute if score @s md_ne matches 1 positioned ~7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
scoreboard players set @s md_roll 1
$execute if score @s md_ns matches 1 positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
scoreboard players set @s md_roll 2
$execute if score @s md_nw matches 1 positioned ~-7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
tag @s remove md.maze.spread_source
