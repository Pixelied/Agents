# Exact-runtime shifting-maze proof begins only after runtime DFS reaches stable phase 2.
scoreboard players set $maze_cells md_tmp 0
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:debug/maze_smoke/count_cells_ctx with storage medusa:macro maze
execute if score $maze_cells md_tmp matches 169 run say MEDUSA_MAZE_CELLS_OK
execute unless score $maze_cells md_tmp matches 169 run say MEDUSA_MAZE_CELLS_FAILED
# Reuse the production flood validator against CURRENT by copying CURRENT into NEXT.
function medusa:maze/propose/copy_current with storage medusa:macro maze
scoreboard players set @s md_mdelta 16
function medusa:maze/validate/start
scoreboard players set $maze_initial_wait md_tmp 0
schedule function medusa:debug/maze_smoke/initial_tick 1t replace
