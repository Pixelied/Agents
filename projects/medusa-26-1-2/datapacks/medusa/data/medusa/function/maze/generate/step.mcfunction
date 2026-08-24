# DFS children record md_mparent so dead ends can backtrack without a preset path.
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/generate/step_ctx with storage medusa:macro maze
