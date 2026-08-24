$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},sort=random,limit=1] at @s run function medusa:maze/propose/mutate_cell with storage medusa:macro maze
