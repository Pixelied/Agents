$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mseen=1}] run scoreboard players add $maze_gen_seen md_tmp 1
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_mparent=1..4}] run scoreboard players add $maze_gen_parented md_tmp 1
$execute as @e[type=minecraft:marker,tag=md.maze.cursor,scores={md_eid=$(eid)}] run scoreboard players add $maze_gen_cursors md_tmp 1
