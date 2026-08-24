summon minecraft:marker 0 110 0 {Tags:["md.debug_marker_probe"]}
execute if entity @e[type=minecraft:marker,tag=md.debug_marker_probe,limit=1] run say MEDUSA_RAW_MARKER_OK
execute unless entity @e[type=minecraft:marker,tag=md.debug_marker_probe,limit=1] run say MEDUSA_RAW_MARKER_MISSING
kill @e[type=minecraft:marker,tag=md.debug_marker_probe]
execute positioned 0 100 0 run function medusa:admin/place_temple
execute if entity @e[type=minecraft:marker,tag=md.instance,limit=1] run say MEDUSA_INSTANCE_IMMEDIATE_OK
execute unless entity @e[type=minecraft:marker,tag=md.instance,limit=1] run say MEDUSA_INSTANCE_IMMEDIATE_MISSING
tag @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] add md.debug_maze_primary
scoreboard players set $maze_wait md_tmp 0
scoreboard players set $maze_smoke_started md_tmp 0
schedule function medusa:debug/wait_for_maze_ready 1t replace
