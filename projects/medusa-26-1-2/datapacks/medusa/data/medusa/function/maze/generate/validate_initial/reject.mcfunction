scoreboard players add @s md_mgen_try 1
execute if score @s md_mgen_try matches 17.. run scoreboard players set @s md_mgen_try 16
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/generate/validate_initial/reset_topology with storage medusa:macro maze
