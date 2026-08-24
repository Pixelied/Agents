$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=1}] at @s run fill ~3 ~1 ~-1 ~4 ~7 ~1 minecraft:air
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=1}] at @s run fill ~-1 ~1 ~3 ~1 ~7 ~4 minecraft:air
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_nn = @s md_mn
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_ne = @s md_me
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_ns = @s md_ms
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players operation @s md_nw = @s md_mw
$tag @e[type=minecraft:marker,tag=md.maze.cursor,scores={md_eid=$(eid)}] remove md.maze.cursor
