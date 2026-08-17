scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..12 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 8 run playsound minecraft:item.shield.block hostile @a[distance=..24] ~ ~ ~ 0.8 0.7
execute if score @s fk_timer matches 13..24 if block ^ ^0.1 ^0.6 minecraft:air if block ^ ^1.2 ^0.6 minecraft:air run tp @s ^ ^ ^0.42
execute if score @s fk_timer matches 13..24 run function fallen_knight:boss/attack/charge/hit
execute if score @s fk_timer matches 25..36 run data merge entity @s {NoAI:1b}
execute if score @s fk_timer matches 37.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 37.. run data merge entity @s {NoAI:0b}
