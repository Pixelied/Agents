execute if score @s md_morient matches 1 run summon minecraft:armor_stand ~3 ~1 ~0 {Tags:["md.maze.occupancy_probe"],Invisible:1b,NoGravity:1b,Invulnerable:1b}
execute if score @s md_morient matches 2 run summon minecraft:armor_stand ~0 ~1 ~3 {Tags:["md.maze.occupancy_probe"],Invisible:1b,NoGravity:1b,Invulnerable:1b}
scoreboard players operation @e[type=minecraft:armor_stand,tag=md.maze.occupancy_probe,distance=..5,limit=1,sort=nearest] md_eid = @s md_eid
