execute as @e[type=minecraft:husk,tag=md.boss] at @s run function medusa:boss/tick_one
execute as @e[type=minecraft:marker,tag=md.venom_projectile] at @s run function medusa:boss/attack/venom_spit/projectile_tick
execute as @e[type=minecraft:marker,tag=md.venom_hazard] at @s run function medusa:boss/attack/venom_spit/hazard_tick
