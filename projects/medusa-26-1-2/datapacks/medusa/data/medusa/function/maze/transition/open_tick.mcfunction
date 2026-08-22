scoreboard players add @s md_mtick 1
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/transition/open_tick_ctx with storage medusa:macro maze
