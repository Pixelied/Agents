$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},sort=random,limit=1] at @s run function medusa:maze/generate/loop_from_cell with storage medusa:macro maze
