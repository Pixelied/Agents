$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mseen 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mfront 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_mdist 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] md_nparent 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=0,md_mcol=0},limit=1] md_mseen 1
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=0,md_mcol=0},limit=1] md_mfront 1
