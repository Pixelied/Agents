$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_me=1,md_ne=0}] at @s run fill ~3 ~1 ~-1 ~4 ~7 ~1 minecraft:stone_bricks
$execute as @e[type=minecraft:marker,tag=md.maze.cell,scores={md_eid=$(eid),md_ms=1,md_ns=0}] at @s run fill ~-1 ~1 ~3 ~1 ~7 ~4 minecraft:stone_bricks
