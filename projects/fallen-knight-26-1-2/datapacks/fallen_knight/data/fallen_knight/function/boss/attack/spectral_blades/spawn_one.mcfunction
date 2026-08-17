summon minecraft:marker ~ ~ ~ {Tags:["fk.spectral","fk.spectral_new"]}
scoreboard players operation @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] fk_aid = @s fk_aid
scoreboard players set @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] fk_timer 0
tag @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] remove fk.spectral_new
