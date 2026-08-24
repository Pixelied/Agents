scoreboard players set $maze_gen_seen md_tmp 0
scoreboard players set $maze_gen_parented md_tmp 0
scoreboard players set $maze_gen_cursors md_tmp 0
execute store result storage medusa:macro maze_diag.eid int 1 run scoreboard players get @s md_eid
function medusa:debug/maze_smoke/prevalidate_state_ctx with storage medusa:macro maze_diag
execute if score $maze_gen_seen md_tmp matches 169 run say MEDUSA_MAZE_DEBUG_GEN_SEEN_169
execute unless score $maze_gen_seen md_tmp matches 169 run say MEDUSA_MAZE_DEBUG_GEN_SEEN_NOT_169
execute if score $maze_gen_parented md_tmp matches 168 run say MEDUSA_MAZE_DEBUG_GEN_PARENTED_168
execute unless score $maze_gen_parented md_tmp matches 168 run say MEDUSA_MAZE_DEBUG_GEN_PARENTED_NOT_168
execute if score $maze_gen_cursors md_tmp matches 1 run say MEDUSA_MAZE_DEBUG_GEN_CURSOR_ONE
execute unless score $maze_gen_cursors md_tmp matches 1 run say MEDUSA_MAZE_DEBUG_GEN_CURSOR_NOT_ONE
