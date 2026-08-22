execute if score @s md_count matches ..168 run say MEDUSA_MAZE_INIT_REJECT_DISCONNECTED
execute if score @s md_count matches 169 if score @s md_tmp matches ..23 run say MEDUSA_MAZE_INIT_REJECT_SHORT
execute if score @s md_count matches 169 if score @s md_tmp matches 24.. run say MEDUSA_MAZE_INIT_REJECT_UNEXPECTED
scoreboard players add @s md_mgen_try 1
execute if score @s md_mgen_try matches 17.. run scoreboard players set @s md_mgen_try 16
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/generate/validate_initial/reset_topology with storage medusa:macro maze
