scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..9 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 10 run function fallen_knight:boss/attack/sweep/hit
execute if score @s fk_timer matches 26.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 26.. run data merge entity @s {NoAI:0b}
