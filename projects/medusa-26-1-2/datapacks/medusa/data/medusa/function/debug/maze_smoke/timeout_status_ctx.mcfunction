$execute if entity @e[type=minecraft:marker,tag=md.maze.cursor,scores={md_eid=$(eid)},limit=1] run say MEDUSA_MAZE_DEBUG_CURSOR_PRESENT
$execute unless entity @e[type=minecraft:marker,tag=md.maze.cursor,scores={md_eid=$(eid)},limit=1] run say MEDUSA_MAZE_DEBUG_CURSOR_MISSING
