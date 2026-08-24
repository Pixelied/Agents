$execute as @e[type=minecraft:marker,tag=md.maze.trap,scores={md_eid=$(eid),md_marmed=1..2}] at @s run function medusa:maze/trap/tick_one
