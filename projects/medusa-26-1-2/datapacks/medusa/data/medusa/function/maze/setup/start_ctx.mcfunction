$kill @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:marker,tag=md.maze.builder,scores={md_eid=$(eid)}]
summon minecraft:marker ~-44 ~-18 ~30 {Tags:["md.maze.builder","md.maze.new_builder"]}
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_builder,limit=1,sort=nearest] md_eid $(eid)
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_builder,limit=1,sort=nearest] md_mrow 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_builder,limit=1,sort=nearest] md_mcol 0
tag @e[type=minecraft:marker,tag=md.maze.new_builder,limit=1,sort=nearest] remove md.maze.new_builder
scoreboard players set @s md_mphase 0
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mdelta 0
scoreboard players set @s md_count 0
