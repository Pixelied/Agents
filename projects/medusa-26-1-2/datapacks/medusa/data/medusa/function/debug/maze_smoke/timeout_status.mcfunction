execute if score @s md_mphase matches 0 run say MEDUSA_MAZE_DEBUG_PHASE0_SETUP
execute if score @s md_mphase matches 1 run say MEDUSA_MAZE_DEBUG_PHASE1_GENERATING
execute if score @s md_mphase matches 2 run say MEDUSA_MAZE_DEBUG_PHASE2_STABLE
execute if score @s md_mphase matches 3.. run say MEDUSA_MAZE_DEBUG_PHASE_LATER
execute if score @s md_count matches ..40 run say MEDUSA_MAZE_DEBUG_COUNT_LOW
execute if score @s md_count matches 41..120 run say MEDUSA_MAZE_DEBUG_COUNT_MID
execute if score @s md_count matches 121..168 run say MEDUSA_MAZE_DEBUG_COUNT_HIGH
execute if score @s md_count matches 169.. run say MEDUSA_MAZE_DEBUG_COUNT_COMPLETE
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
scoreboard players set $maze_diag_cells md_tmp 0
function medusa:debug/maze_smoke/count_cells_ctx with storage medusa:macro maze
scoreboard players operation $maze_diag_cells md_tmp = $maze_cells md_tmp
execute if score $maze_diag_cells md_tmp matches 169 run say MEDUSA_MAZE_DEBUG_CELLS_169
execute unless score $maze_diag_cells md_tmp matches 169 run say MEDUSA_MAZE_DEBUG_CELLS_NOT_169
function medusa:debug/maze_smoke/timeout_status_ctx with storage medusa:macro maze
