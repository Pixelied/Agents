scoreboard players set @s md_attack 3
scoreboard players set @s md_atk_timer 0
summon minecraft:marker ~ ~1.4 ~ {Tags:["md.venom_projectile","md.new_venom_projectile"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.new_venom_projectile,limit=1,sort=nearest] md_eid = @s md_eid
scoreboard players set @e[type=minecraft:marker,tag=md.new_venom_projectile,limit=1,sort=nearest] md_timer 0
tag @e[type=minecraft:marker,tag=md.new_venom_projectile,limit=1,sort=nearest] remove md.new_venom_projectile
playsound minecraft:entity.llama.spit hostile @a[distance=..36] ~ ~ ~ 1.1 0.7
