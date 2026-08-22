execute store result storage medusa:macro wall.eid int 1 run scoreboard players get @s md_eid
execute store result storage medusa:macro wall.row int 1 run scoreboard players get @s md_mrow
execute store result storage medusa:macro wall.col int 1 run scoreboard players get @s md_mcol
execute store result storage medusa:macro wall.orient int 1 run scoreboard players get @s md_morient
function medusa:maze/wall/animate_close_ctx with storage medusa:macro wall
