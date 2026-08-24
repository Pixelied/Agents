say MEDUSA_MAZE_INITIAL_SOLVABLE_OK
scoreboard players set @s md_mphase 2
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mdelta 0
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:debug/maze_smoke/check_traps_ctx with storage medusa:macro maze
function medusa:maze/propose/start
scoreboard players set $maze_proposal_wait md_tmp 0
schedule function medusa:debug/maze_smoke/proposal_tick 1t replace
