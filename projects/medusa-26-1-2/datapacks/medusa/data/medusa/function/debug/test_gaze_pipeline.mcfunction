tag @e[type=minecraft:husk,tag=md.gaze_target] remove md.gaze_target
kill @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer]
kill @e[type=minecraft:husk,tag=md.gaze_probe_target]
summon minecraft:armor_stand 16 200 16 {Tags:["md.gaze_probe_viewer"],Invisible:1b,NoGravity:1b,Marker:1b}
summon minecraft:husk 16 200 24 {Tags:["md.gaze_probe_target","md.gaze_target"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}
execute as @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] at @s run tp @s ~ ~ ~ facing entity @e[type=minecraft:husk,tag=md.gaze_probe_target,limit=1] eyes
scoreboard players set @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] md_petr 0
scoreboard players set @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] md_angle_ok 0
scoreboard players set @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] md_los_ok 0
scoreboard players set @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] md_gaze_hit 0
scoreboard players set @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] md_ray 0
execute as @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,limit=1] at @s run function medusa:gaze/check_angle
execute if entity @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,scores={md_angle_ok=1..},limit=1] run say MEDUSA_GAZE_ANGLE_OK
execute unless entity @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,scores={md_angle_ok=1..},limit=1] run say MEDUSA_GAZE_ANGLE_FAILED
execute if entity @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,scores={md_los_ok=1..},limit=1] run say MEDUSA_GAZE_LOS_OK
execute unless entity @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,scores={md_los_ok=1..},limit=1] run say MEDUSA_GAZE_LOS_FAILED
execute if entity @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,scores={md_petr=1..},limit=1] run say MEDUSA_GAZE_PETRIFICATION_OK
execute unless entity @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer,scores={md_petr=1..},limit=1] run say MEDUSA_GAZE_PETRIFICATION_FAILED
kill @e[type=minecraft:armor_stand,tag=md.gaze_probe_viewer]
kill @e[type=minecraft:husk,tag=md.gaze_probe_target]
