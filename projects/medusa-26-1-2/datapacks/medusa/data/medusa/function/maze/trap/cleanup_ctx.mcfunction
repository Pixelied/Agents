$kill @e[tag=md.maze.trap_helper,scores={md_eid=$(eid)}]
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.trap,scores={md_eid=$(eid)}] md_marmed 0
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.trap,scores={md_eid=$(eid)}] md_mtrap_timer 0
