$execute as @e[type=minecraft:marker,tag=md.maze.cursor,scores={md_eid=$(eid)},limit=1] at @s run function medusa:maze/generate/try_direction with storage medusa:macro maze
