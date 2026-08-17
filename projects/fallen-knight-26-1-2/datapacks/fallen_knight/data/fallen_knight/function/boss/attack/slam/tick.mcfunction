scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..23 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 8 run particle minecraft:witch ~ ~0.1 ~ 1.0 0.02 1.0 0.02 8 force @a[distance=..24]
execute if score @s fk_timer matches 16 run particle minecraft:witch ~ ~0.1 ~ 1.8 0.02 1.8 0.02 12 force @a[distance=..24]
execute if score @s fk_timer matches 24 run function fallen_knight:boss/attack/slam/hit
execute if score @s fk_timer matches 46.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 46.. run data merge entity @s {NoAI:0b}
