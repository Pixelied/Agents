scoreboard players set @s md_ray 0
execute anchored eyes facing entity @e[type=minecraft:husk,tag=md.gaze_target,limit=1] eyes positioned ^ ^ ^0.5 run function medusa:gaze/los_ray
