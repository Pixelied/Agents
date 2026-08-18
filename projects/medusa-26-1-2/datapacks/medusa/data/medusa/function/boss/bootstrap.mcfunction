execute store result storage medusa:macro boss.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/kill_existing with storage medusa:macro boss
summon minecraft:husk ~64 ~-17 ~72 {Tags:["md.boss","md.new_boss"],PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b}
scoreboard players operation @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] md_eid = @s md_eid
execute as @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] run attribute @s minecraft:max_health base set 300
execute as @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] run attribute @s minecraft:attack_damage base set 0
execute as @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] run attribute @s minecraft:scale base set 1.4
execute as @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] run data merge entity @s {Health:300.0f,PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b,CustomName:'{"text":"Medusa","color":"dark_green","bold":true}',CustomNameVisible:0b,DeathLootTable:"medusa:entity/empty"}
scoreboard players set @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] md_phase 1
scoreboard players set @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] md_timer 0
scoreboard players set @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] md_attack 0
scoreboard players set @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] md_cd 20
function medusa:boss/health/apply_scale with storage medusa:macro boss
execute store result storage medusa:macro boss.maxhp int 1 run scoreboard players get @s md_maxhp
function medusa:boss/bossbar/create with storage medusa:macro boss
tag @e[type=minecraft:husk,tag=md.new_boss,limit=1,sort=nearest] remove md.new_boss
