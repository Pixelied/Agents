execute if score @s md_mtry matches ..63 run function medusa:maze/propose/start
execute if score @s md_mtry matches ..63 run scoreboard players set $maze_proposal_wait md_tmp 0
execute if score @s md_mtry matches ..63 run schedule function medusa:debug/maze_smoke/proposal_tick 1t replace
execute if score @s md_mtry matches 64.. run say MEDUSA_MAZE_DELTA_FAILED
