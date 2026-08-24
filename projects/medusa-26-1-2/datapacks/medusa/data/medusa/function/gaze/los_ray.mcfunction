scoreboard players add @s md_ray 1
execute if block ~ ~ ~ #medusa:gaze_passable if entity @e[type=minecraft:husk,tag=md.gaze_target,distance=..3,limit=1] run function medusa:gaze/los_hit
execute if block ~ ~ ~ #medusa:gaze_passable unless entity @e[type=minecraft:husk,tag=md.gaze_target,distance=..3,limit=1] if score @s md_ray matches ..95 positioned ^ ^ ^0.5 run function medusa:gaze/los_ray
