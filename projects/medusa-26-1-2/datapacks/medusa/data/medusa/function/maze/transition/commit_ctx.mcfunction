$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_mn = @s md_nn
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_me = @s md_ne
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_ms = @s md_ns
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_mw = @s md_nw
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mseen 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mfront 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mblocked 0
