# 35 combo, 30 overhead, 20 guard, 15 charge.
execute if score @s fk_roll matches 1..35 unless score @s fk_prev matches 3 if score @s fk_cd_combo matches 0 run function fallen_knight:boss/attack/knights_combo/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 36..65 unless score @s fk_prev matches 4 if score @s fk_cd_over matches 0 run function fallen_knight:boss/attack/overhead/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 66..85 unless score @s fk_prev matches 1 if score @s fk_cd_guard matches 0 run function fallen_knight:boss/attack/guard/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 86..100 unless score @s fk_prev matches 5 if score @s fk_cd_charge matches 0 run function fallen_knight:boss/attack/charge/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 3 if score @s fk_cd_combo matches 0 run function fallen_knight:boss/attack/knights_combo/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 4 if score @s fk_cd_over matches 0 run function fallen_knight:boss/attack/overhead/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 1 if score @s fk_cd_guard matches 0 run function fallen_knight:boss/attack/guard/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 5 if score @s fk_cd_charge matches 0 run function fallen_knight:boss/attack/charge/start
