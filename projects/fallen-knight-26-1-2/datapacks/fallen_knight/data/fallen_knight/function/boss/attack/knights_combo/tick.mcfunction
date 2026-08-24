scoreboard players add @s fk_timer 1
execute if score @s fk_timer matches 1..7 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 8 run function fallen_knight:boss/attack/knights_combo/hit_1
execute if score @s fk_timer matches 10 run data merge entity @s {NoAI:0b}
execute if score @s fk_timer matches 11..16 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 17 run function fallen_knight:boss/attack/knights_combo/hit_2
execute if score @s fk_timer matches 19 run data merge entity @s {NoAI:0b}
execute if score @s fk_timer matches 20..29 run function fallen_knight:boss/director/face_target
execute if score @s fk_timer matches 30 run function fallen_knight:boss/attack/knights_combo/hit_3
execute if score @s fk_timer matches 31 run data merge entity @s {NoAI:1b}
execute if score @s fk_timer matches 47.. run scoreboard players set @s fk_attack 0
execute if score @s fk_timer matches 47.. run data merge entity @s {NoAI:0b}
