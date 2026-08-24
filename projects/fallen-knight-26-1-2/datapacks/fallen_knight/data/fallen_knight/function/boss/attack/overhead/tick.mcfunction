scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..18 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 10 run playsound minecraft:entity.player.attack.strong hostile @a[distance=..20] ~ ~ ~ 1 0.45
execute if score @s fk_timer matches 19 run function fallen_knight:boss/attack/overhead/hit
execute if score @s fk_timer matches 19 run particle minecraft:poof ^ ^0.1 ^1.8 0.3 0.05 0.3 0.02 8 force @a[distance=..24]
execute if score @s fk_timer matches 19 run playsound minecraft:block.anvil.land hostile @a[distance=..24] ~ ~ ~ 0.6 1.35
execute if score @s fk_timer matches 41.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 41.. run data merge entity @s {NoAI:0b}
