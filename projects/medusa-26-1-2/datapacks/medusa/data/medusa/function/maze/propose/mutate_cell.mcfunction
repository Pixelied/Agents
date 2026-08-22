scoreboard players set @s md_mblocked 0
execute store result score @s md_roll run random value 1..2
# Canonical east edge: md_mfront=1 means this edge was already changed in this proposal.
execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 run scoreboard players operation @s md_tmp = @s md_ne
execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 if score @s md_tmp matches 0 run scoreboard players set @s md_ne 1
$execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 if score @s md_tmp matches 0 positioned ~7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] run scoreboard players set @s md_nw 1
execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 if score @s md_tmp matches 1 run scoreboard players set @s md_ne 0
$execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 if score @s md_tmp matches 1 positioned ~7 ~ ~ as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] run scoreboard players set @s md_nw 0
execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 run scoreboard players set @s md_mblocked 1
execute if score @s md_roll matches 1 if score @s md_mcol matches ..11 if score @s md_mfront matches 0 run scoreboard players set @s md_mfront 1
# Canonical south edge: md_mseen=1 means this edge was already changed in this proposal.
execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 run scoreboard players operation @s md_tmp = @s md_ns
execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 if score @s md_tmp matches 0 run scoreboard players set @s md_ns 1
$execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 if score @s md_tmp matches 0 positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] run scoreboard players set @s md_nn 1
execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 if score @s md_tmp matches 1 run scoreboard players set @s md_ns 0
$execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 if score @s md_tmp matches 1 positioned ~ ~ ~7 as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] run scoreboard players set @s md_nn 0
execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 run scoreboard players set @s md_mblocked 1
execute if score @s md_roll matches 2 if score @s md_mrow matches ..11 if score @s md_mseen matches 0 run scoreboard players set @s md_mseen 1
$execute if score @s md_mblocked matches 1 run scoreboard players add @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_mdelta 1
