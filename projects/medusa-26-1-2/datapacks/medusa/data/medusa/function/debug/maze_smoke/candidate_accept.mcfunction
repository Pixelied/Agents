execute if score @s md_mdelta matches 16..28 run say MEDUSA_MAZE_DELTA_OK
execute unless score @s md_mdelta matches 16..28 run say MEDUSA_MAZE_DELTA_FAILED
function medusa:debug/maze_smoke/prepare_isolation
function medusa:maze/transition/start_open
function medusa:debug/maze_smoke/check_open_started
scoreboard players set $maze_open_wait md_tmp 0
schedule function medusa:debug/maze_smoke/open_tick 1t replace
