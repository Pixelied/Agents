# 30 bash, 30 combo, 20 guard, 20 overhead; blocked rolls fall through deterministically.
execute if score @s fk_roll matches 1..30 unless score @s fk_prev matches 2 if score @s fk_cd_bash matches 0 run function fallen_knight:boss/attack/shield_bash/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 31..60 unless score @s fk_prev matches 3 if score @s fk_cd_combo matches 0 run function fallen_knight:boss/attack/knights_combo/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 61..80 unless score @s fk_prev matches 1 if score @s fk_cd_guard matches 0 run function fallen_knight:boss/attack/guard/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 81..100 unless score @s fk_prev matches 4 if score @s fk_cd_over matches 0 run function fallen_knight:boss/attack/overhead/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 2 if score @s fk_cd_bash matches 0 run function fallen_knight:boss/attack/shield_bash/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 3 if score @s fk_cd_combo matches 0 run function fallen_knight:boss/attack/knights_combo/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 1 if score @s fk_cd_guard matches 0 run function fallen_knight:boss/attack/guard/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 4 if score @s fk_cd_over matches 0 run function fallen_knight:boss/attack/overhead/start
