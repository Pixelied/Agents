$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=0,md_ne=1}] at @s run fill ~3 ~1 ~-1 ~4 ~7 ~1 minecraft:air
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=0,md_ns=1}] at @s run fill ~-1 ~1 ~3 ~1 ~7 ~4 minecraft:air
