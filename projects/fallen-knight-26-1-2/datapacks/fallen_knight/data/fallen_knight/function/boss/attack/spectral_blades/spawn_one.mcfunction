summon minecraft:marker ~ ~ ~
tag @e[type=minecraft:marker,tag=!fk.spectral,tag=!fk.wave,tag=!fk.arena,sort=nearest,limit=1,distance=..0.2] add fk.spectral_new
tag @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] add fk.spectral
scoreboard players operation @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] fk_aid = @s fk_aid
scoreboard players set @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] fk_timer 0
tag @e[type=minecraft:marker,tag=fk.spectral_new,sort=nearest,limit=1,distance=..1] remove fk.spectral_new
