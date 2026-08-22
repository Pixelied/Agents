execute if score @s md_morient matches 1 run summon minecraft:marker ~3 ~1 ~0 {Tags:["md.maze.occupancy_probe"]}
execute if score @s md_morient matches 2 run summon minecraft:marker ~0 ~1 ~3 {Tags:["md.maze.occupancy_probe"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.occupancy_probe,distance=..5,limit=1,sort=nearest] md_eid = @s md_eid
