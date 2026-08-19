scoreboard players set @s md_gaze_hit 1
scoreboard players set @s md_los_ok 1
execute if entity @e[type=minecraft:husk,tag=md.gaze_target,scores={md_gorgon_active=1..},distance=..3,limit=1] run function medusa:gaze/apply_gorgon
execute unless entity @e[type=minecraft:husk,tag=md.gaze_target,scores={md_gorgon_active=1..},distance=..3,limit=1] run function medusa:gaze/apply_normal
