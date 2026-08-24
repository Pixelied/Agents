$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mfront=1}] at @s run function medusa:maze/validate/spread_cell with storage medusa:macro maze
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mfront=2}] md_mfront 1
scoreboard players set @s md_count 0
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=1}] run scoreboard players add @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_count 1
$scoreboard players operation @s md_tmp = @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mrow=12,md_mcol=12},limit=1] md_mdist
execute if score @s md_count matches 169 if score @s md_tmp matches 24.. run function medusa:maze/validate/accept
execute if score @s md_count matches 169 if score @s md_tmp matches ..23 run function medusa:maze/validate/reject
$execute unless entity @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mfront=1}] if score @s md_count matches ..168 run function medusa:maze/validate/reject
