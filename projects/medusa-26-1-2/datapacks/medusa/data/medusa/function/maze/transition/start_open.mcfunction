scoreboard players set @s md_mphase 6
scoreboard players set @s md_mtick 0
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/transition/start_open_ctx with storage medusa:macro maze
