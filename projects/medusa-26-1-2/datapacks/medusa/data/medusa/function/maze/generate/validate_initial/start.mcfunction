execute if entity @s[tag=md.debug_maze_primary] run function medusa:debug/maze_smoke/prevalidate_state
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/propose/copy_current with storage medusa:macro maze
execute if entity @s[tag=md.debug_maze_primary] run function medusa:debug/maze_smoke/postcopy_state
scoreboard players set @s md_mmode 90
function medusa:maze/validate/start
