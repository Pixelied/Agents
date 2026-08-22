tag @e[type=minecraft:marker,tag=md.maze.cell,tag=md.debug_recovery_edge] remove md.debug_recovery_edge
$tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=1},limit=1,sort=nearest] add md.debug_recovery_edge
