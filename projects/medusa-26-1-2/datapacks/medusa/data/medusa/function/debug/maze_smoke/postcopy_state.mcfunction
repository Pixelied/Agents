scoreboard players set $maze_copy_mismatch md_tmp 0
function medusa:debug/maze_smoke/postcopy_state_ctx with storage medusa:macro maze_diag
execute if score $maze_copy_mismatch md_tmp matches 0 run say MEDUSA_MAZE_DEBUG_COPY_OK
execute unless score $maze_copy_mismatch md_tmp matches 0 run say MEDUSA_MAZE_DEBUG_COPY_MISMATCH
