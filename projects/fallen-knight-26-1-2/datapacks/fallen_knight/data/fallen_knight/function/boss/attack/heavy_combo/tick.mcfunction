scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..9 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 10 run function fallen_knight:boss/attack/heavy_combo/hit_1
execute if score @s fk_timer matches 11..19 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 20 run function fallen_knight:boss/attack/heavy_combo/hit_2
execute if score @s fk_timer matches 21 run data merge entity @s {NoAI:1b}
execute if score @s fk_timer matches 37 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 38 run function fallen_knight:boss/attack/heavy_combo/hit_3
execute if score @s fk_timer matches 55.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 55.. run data merge entity @s {NoAI:0b}
