scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..9 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 6 run playsound minecraft:item.shield.block hostile @a[distance=..20] ~ ~ ~ 0.8 0.6
execute if score @s fk_timer matches 10 run function fallen_knight:boss/attack/shield_bash/hit
execute if score @s fk_timer matches 25.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 25.. run data merge entity @s {NoAI:0b}
