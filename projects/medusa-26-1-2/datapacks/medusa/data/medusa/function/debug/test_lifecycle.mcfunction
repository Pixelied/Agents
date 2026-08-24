execute as @e[type=minecraft:marker,tag=md.instance,limit=1,sort=nearest] at @s run function medusa:debug/test_lifecycle_instance
execute unless entity @e[type=minecraft:marker,tag=md.instance,limit=1] run say MEDUSA_LIFECYCLE_INSTANCE_FAILED
