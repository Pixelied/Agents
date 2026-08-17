summon minecraft:marker ^ ^0.2 ^1.5 {Tags:["fk.wave","fk.wave_new"]}
scoreboard players operation @e[type=minecraft:marker,tag=fk.wave_new,sort=nearest,limit=1,distance=..3] fk_aid = @s fk_aid
scoreboard players set @e[type=minecraft:marker,tag=fk.wave_new,sort=nearest,limit=1,distance=..3] fk_timer 0
data modify entity @e[type=minecraft:marker,tag=fk.wave_new,sort=nearest,limit=1,distance=..3] Rotation set from entity @s Rotation
tag @e[type=minecraft:marker,tag=fk.wave_new,sort=nearest,limit=1,distance=..3] remove fk.wave_new
playsound minecraft:entity.evoker.cast_spell hostile @a[distance=..24] ~ ~ ~ 0.7 1.35
