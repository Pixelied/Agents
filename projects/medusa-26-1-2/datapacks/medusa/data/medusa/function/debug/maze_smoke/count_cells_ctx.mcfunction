$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)}] run scoreboard players add $maze_cells md_tmp 1
