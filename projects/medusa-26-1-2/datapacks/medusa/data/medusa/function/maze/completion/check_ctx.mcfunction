# Arm completion only after an eligible participant reaches the southeast maze exit from the labyrinth side.
$execute positioned ~37 ~-17 ~111 as @a[gamemode=survival,scores={md_eid=$(eid)},dx=6,dy=8,dz=6] run tag @s add md.maze.sanctum_approach
$execute positioned ~37 ~-17 ~111 as @a[gamemode=adventure,scores={md_eid=$(eid)},dx=6,dy=8,dz=6] run tag @s add md.maze.sanctum_approach
# Exact-runtime probe mirrors the same two-step crossing without changing player eligibility.
$execute positioned ~37 ~-17 ~111 as @e[type=minecraft:marker,tag=md.maze.completion_probe,scores={md_eid=$(eid)},dx=6,dy=8,dz=6] run tag @s add md.maze.sanctum_approach
# Crossing east through the carved threshold into the sanctum completes the first-clear dungeon.
$execute positioned ~45 ~-17 ~111 if entity @a[tag=md.maze.sanctum_approach,gamemode=survival,scores={md_eid=$(eid)},dx=5,dy=8,dz=6] run function medusa:maze/completion/complete
$execute positioned ~45 ~-17 ~111 if entity @a[tag=md.maze.sanctum_approach,gamemode=adventure,scores={md_eid=$(eid)},dx=5,dy=8,dz=6] run function medusa:maze/completion/complete
$execute positioned ~45 ~-17 ~111 if entity @e[type=minecraft:marker,tag=md.maze.completion_probe,tag=md.maze.sanctum_approach,scores={md_eid=$(eid)},dx=5,dy=8,dz=6] run function medusa:maze/completion/complete
