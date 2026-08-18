scoreboard players set @s md_attack 4
scoreboard players set @s md_atk_timer 0
summon minecraft:endermite ~1 ~ ~ {Tags:["md.snake","md.new_snake"],PersistenceRequired:1b}
summon minecraft:endermite ~-1 ~ ~0.6 {Tags:["md.snake","md.new_snake"],PersistenceRequired:1b}
summon minecraft:endermite ~0.5 ~ ~-1 {Tags:["md.snake","md.new_snake"],PersistenceRequired:1b}
scoreboard players operation @e[type=minecraft:endermite,tag=md.new_snake,distance=..4] md_eid = @s md_eid
execute as @e[type=minecraft:endermite,tag=md.new_snake,distance=..4] run attribute @s minecraft:max_health base set 4
execute as @e[type=minecraft:endermite,tag=md.new_snake,distance=..4] run attribute @s minecraft:attack_damage base set 2
execute as @e[type=minecraft:endermite,tag=md.new_snake,distance=..4] run data modify entity @s Health set value 4.0f
tag @e[type=minecraft:endermite,tag=md.new_snake,distance=..4] remove md.new_snake
playsound minecraft:entity.silverfish.ambient hostile @a[distance=..36] ~ ~ ~ 1.4 0.6
