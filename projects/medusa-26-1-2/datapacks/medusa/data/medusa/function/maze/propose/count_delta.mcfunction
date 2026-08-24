scoreboard players set @s md_mdelta 0
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/propose/count_delta_ctx with storage medusa:macro maze
