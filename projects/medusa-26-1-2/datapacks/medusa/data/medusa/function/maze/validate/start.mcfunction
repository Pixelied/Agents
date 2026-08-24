execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/validate/start_ctx with storage medusa:macro maze
scoreboard players set @s md_mphase 4
scoreboard players set @s md_mtick 0
scoreboard players set @s md_count 0
