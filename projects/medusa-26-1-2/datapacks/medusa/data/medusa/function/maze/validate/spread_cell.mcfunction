tag @s add md.maze.spread_source
scoreboard players set @s md_mfront 0
$execute if score @s md_nn matches 1 positioned ~ ~ ~-7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
$execute if score @s md_ne matches 1 positioned ~7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
$execute if score @s md_ns matches 1 positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
$execute if score @s md_nw matches 1 positioned ~-7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=0},distance=..1,limit=1] at @s run function medusa:maze/validate/visit_neighbor
tag @s remove md.maze.spread_source
