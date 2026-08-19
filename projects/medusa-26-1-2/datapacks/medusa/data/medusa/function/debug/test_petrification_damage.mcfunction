kill @e[type=minecraft:husk,tag=md.damage_probe]
summon minecraft:husk ~ ~ ~ {Tags:["md.damage_probe"],NoAI:1b,Health:20.0f,PersistenceRequired:1b}
effect give @e[type=minecraft:husk,tag=md.damage_probe,limit=1,sort=nearest] minecraft:resistance 10 4 true
execute store success score $clear_success md_tmp run effect clear @e[type=minecraft:husk,tag=md.damage_probe,limit=1,sort=nearest] minecraft:resistance
execute if score $clear_success md_tmp matches 1 run say MEDUSA_RESISTANCE_CLEAR_OK
execute unless score $clear_success md_tmp matches 1 run say MEDUSA_RESISTANCE_CLEAR_FAILED
execute store success score $damage_success md_tmp run damage @e[type=minecraft:husk,tag=md.damage_probe,limit=1,sort=nearest] 2 medusa:petrification
execute if score $damage_success md_tmp matches 1 run say MEDUSA_DAMAGE_COMMAND_OK
execute unless score $damage_success md_tmp matches 1 run say MEDUSA_DAMAGE_COMMAND_FAILED
effect give @e[type=minecraft:husk,tag=md.damage_probe,limit=1,sort=nearest] minecraft:resistance 2 4 true
execute store result score $damage_probe md_tmp run data get entity @e[type=minecraft:husk,tag=md.damage_probe,limit=1,sort=nearest] Health 1
execute if score $damage_probe md_tmp matches 20 run say MEDUSA_DAMAGE_HEALTH_20
execute if score $damage_probe md_tmp matches 19 run say MEDUSA_DAMAGE_HEALTH_19
execute if score $damage_probe md_tmp matches 18 run say MEDUSA_DAMAGE_HEALTH_18
execute if score $damage_probe md_tmp matches 17 run say MEDUSA_DAMAGE_HEALTH_17
execute if score $damage_probe md_tmp matches 16 run say MEDUSA_DAMAGE_HEALTH_16
execute if score $damage_probe md_tmp matches 18 run say MEDUSA_PETRIFICATION_DAMAGE_OK
execute unless score $damage_probe md_tmp matches 18 run say MEDUSA_PETRIFICATION_DAMAGE_FAILED
kill @e[type=minecraft:husk,tag=md.damage_probe]
