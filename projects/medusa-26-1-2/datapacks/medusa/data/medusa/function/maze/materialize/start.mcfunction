execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/materialize/current with storage medusa:macro maze
scoreboard players set @s md_mphase 2
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mdelta 0
function medusa:maze/trap/setup
