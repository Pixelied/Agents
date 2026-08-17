scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..12 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 16..23 if block ^ ^0.1 ^0.7 minecraft:air if block ^ ^1.2 ^0.7 minecraft:air run tp @s ^ ^ ^0.55
execute if score @s fk_timer matches 16..23 run function fallen_knight:boss/attack/lunge/hit
execute if score @s fk_timer matches 39.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 39.. run data merge entity @s {NoAI:0b}
