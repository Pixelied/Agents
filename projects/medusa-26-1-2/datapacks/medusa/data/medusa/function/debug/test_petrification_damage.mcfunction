kill @e[type=minecraft:husk,tag=md.damage_probe]
summon minecraft:husk 8 101 0 {Tags:["md.damage_probe"],NoAI:1b,Health:20.0f,PersistenceRequired:1b}
effect give @e[type=minecraft:husk,tag=md.damage_probe,limit=1] minecraft:resistance 10 4 true
effect clear @e[type=minecraft:husk,tag=md.damage_probe,limit=1] minecraft:resistance
damage @e[type=minecraft:husk,tag=md.damage_probe,limit=1] 2 medusa:petrification
effect give @e[type=minecraft:husk,tag=md.damage_probe,limit=1] minecraft:resistance 2 4 true
execute store result score $damage_probe md_tmp run data get entity @e[type=minecraft:husk,tag=md.damage_probe,limit=1] Health 1
execute if score $damage_probe md_tmp matches 18 run say MEDUSA_PETRIFICATION_DAMAGE_OK
execute unless score $damage_probe md_tmp matches 18 run say MEDUSA_PETRIFICATION_DAMAGE_FAILED
kill @e[type=minecraft:husk,tag=md.damage_probe]
