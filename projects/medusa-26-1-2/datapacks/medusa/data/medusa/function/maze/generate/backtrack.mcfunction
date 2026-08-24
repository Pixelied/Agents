$execute if score @s md_mparent matches 1 positioned ~ ~ ~-7 run tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] add md.maze.next_cursor
$execute if score @s md_mparent matches 2 positioned ~7 ~ ~ run tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] add md.maze.next_cursor
$execute if score @s md_mparent matches 3 positioned ~ ~ ~7 run tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] add md.maze.next_cursor
$execute if score @s md_mparent matches 4 positioned ~-7 ~ ~ run tag @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid)},distance=..1,limit=1] add md.maze.next_cursor
tag @s remove md.maze.cursor
$tag @e[type=minecraft:marker,tag=md.maze.next_cursor,scores={md_eid=$(eid)}] add md.maze.cursor
$tag @e[type=minecraft:marker,tag=md.maze.next_cursor,scores={md_eid=$(eid)}] remove md.maze.next_cursor
