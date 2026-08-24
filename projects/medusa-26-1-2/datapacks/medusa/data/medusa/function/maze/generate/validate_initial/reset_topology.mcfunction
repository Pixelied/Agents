$tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] remove md.maze.cursor
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mn 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_me 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_ms 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mw 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_nn 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_ne 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_ns 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_nw 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mseen 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mfront 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mdist 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mparent 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mblocked 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=0,md_mcol=0},limit=1] md_mseen 1
$tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=0,md_mcol=0},limit=1] add md.maze.cursor
scoreboard players set @s md_count 1
scoreboard players set @s md_mphase 1
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mdelta 0
scoreboard players set @s md_mmode 0
