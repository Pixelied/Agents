forceload add 0 0 96 96
summon minecraft:marker 0 110 0 {Tags:["md.debug_marker_probe"]}
execute if entity @e[type=minecraft:marker,tag=md.debug_marker_probe,limit=1] run say MEDUSA_RAW_MARKER_OK
execute unless entity @e[type=minecraft:marker,tag=md.debug_marker_probe,limit=1] run say MEDUSA_RAW_MARKER_MISSING
kill @e[type=minecraft:marker,tag=md.debug_marker_probe]
execute positioned 0 100 0 run function medusa:admin/place_temple
execute if entity @e[type=minecraft:marker,tag=md.instance,limit=1] run say MEDUSA_INSTANCE_IMMEDIATE_OK
execute unless entity @e[type=minecraft:marker,tag=md.instance,limit=1] run say MEDUSA_INSTANCE_IMMEDIATE_MISSING
