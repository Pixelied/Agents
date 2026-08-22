scoreboard players set $maze_has_open md_tmp 0
scoreboard players set $maze_has_close md_tmp 0
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:debug/maze_smoke/scan_delta_ctx with storage medusa:macro maze
execute if score $maze_has_open md_tmp matches 1 if score $maze_has_close md_tmp matches 1 run function medusa:debug/maze_smoke/candidate_accept
execute unless score $maze_has_open md_tmp matches 1 run function medusa:debug/maze_smoke/candidate_retry
execute if score $maze_has_open md_tmp matches 1 unless score $maze_has_close md_tmp matches 1 run function medusa:debug/maze_smoke/candidate_retry
