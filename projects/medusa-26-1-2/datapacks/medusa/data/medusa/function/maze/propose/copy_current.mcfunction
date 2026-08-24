$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_nn = @s md_mn
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_ne = @s md_me
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_ns = @s md_ms
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_nw = @s md_mw
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mseen 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mfront 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mblocked 0
