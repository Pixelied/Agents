scoreboard players set @s md_ns 1
$execute positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] run scoreboard players set @s md_nn 1
scoreboard players set @s md_mseen 1
$scoreboard players add @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_mdelta 1
