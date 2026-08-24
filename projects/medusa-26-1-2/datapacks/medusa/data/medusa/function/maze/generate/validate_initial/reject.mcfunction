execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches ..168 run say MEDUSA_MAZE_INIT_REJECT_DISCONNECTED
execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches ..1 run say MEDUSA_MAZE_INIT_REACH_0_1
execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches 2..40 run say MEDUSA_MAZE_INIT_REACH_2_40
execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches 41..120 run say MEDUSA_MAZE_INIT_REACH_41_120
execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches 121..168 run say MEDUSA_MAZE_INIT_REACH_121_168
execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches 169 if score @s md_tmp matches ..23 run say MEDUSA_MAZE_INIT_REJECT_SHORT
execute if entity @s[tag=md.debug_maze_primary] if score @s md_count matches 169 if score @s md_tmp matches 24.. run say MEDUSA_MAZE_INIT_REJECT_UNEXPECTED
scoreboard players add @s md_mgen_try 1
execute if score @s md_mgen_try matches 17.. run scoreboard players set @s md_mgen_try 16
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/generate/validate_initial/reset_topology with storage medusa:macro maze
