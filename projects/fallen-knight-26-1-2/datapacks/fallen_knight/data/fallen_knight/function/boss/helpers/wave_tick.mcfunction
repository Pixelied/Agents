scoreboard players add @s fk_timer 1
tp @s ^ ^ ^0.8
particle minecraft:witch ~ ~0.25 ~ 0.15 0.06 0.15 0.01 2 force @a[distance=..24]
execute if score @s fk_timer matches 4 run function fallen_knight:boss/helpers/wave_hit
execute if score @s fk_timer matches 8.. run kill @s
