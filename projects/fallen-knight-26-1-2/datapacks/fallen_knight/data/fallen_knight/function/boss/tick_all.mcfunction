execute as @e[tag=fk.boss] at @s run function fallen_knight:boss/tick_one
execute as @e[type=minecraft:marker,tag=fk.wave] at @s run function fallen_knight:boss/helpers/wave_tick
execute as @e[type=minecraft:marker,tag=fk.spectral] at @s run function fallen_knight:boss/helpers/spectral_tick
