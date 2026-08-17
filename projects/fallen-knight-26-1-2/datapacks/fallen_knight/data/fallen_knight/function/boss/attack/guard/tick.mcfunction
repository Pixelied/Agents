scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..27 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 8 run playsound minecraft:item.shield.block hostile @a[distance=..20] ~ ~ ~ 0.8 0.75
execute if score @s fk_timer matches 8..27 run effect give @s minecraft:resistance 1 3 true
execute if score @s fk_timer matches 39.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 39.. run data merge entity @s {NoAI:0b}
