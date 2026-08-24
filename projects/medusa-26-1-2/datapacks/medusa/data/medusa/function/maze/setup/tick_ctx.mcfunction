$execute as @e[type=minecraft:marker,tag=md.maze.builder,scores={md_eid=$(eid)},limit=1] at @s run function medusa:maze/setup/spawn_cells
$execute if entity @e[type=minecraft:marker,tag=md.maze.builder,scores={md_eid=$(eid),md_mrow=13},limit=1] run function medusa:maze/setup/finish with storage medusa:macro maze
