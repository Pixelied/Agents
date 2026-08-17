scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..19 run particle minecraft:witch ~ ~0.08 ~ 0.22 1.2 0.22 0.01 2 force @a[distance=..24]
execute if score @s fk_timer matches 20 run function fallen_knight:boss/helpers/spectral_hit
execute if score @s fk_timer matches 21.. run kill @s
