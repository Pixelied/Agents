$tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=0,md_mcol=0},limit=1] add md.maze.cursor
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=0,md_mcol=0},limit=1] md_mseen 1
$kill @e[type=minecraft:marker,tag=md.maze.builder,scores={md_eid=$(eid)}]
scoreboard players set @s md_count 1
scoreboard players set @s md_mphase 1
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
