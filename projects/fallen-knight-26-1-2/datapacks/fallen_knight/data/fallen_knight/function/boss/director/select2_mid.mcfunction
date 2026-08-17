execute if score @s fk_roll matches 1..25 unless score @s fk_prev matches 9 if score @s fk_cd_hcombo matches 0 run function fallen_knight:boss/attack/heavy_combo/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 26..50 unless score @s fk_prev matches 8 if score @s fk_cd_slash matches 0 run function fallen_knight:boss/attack/cursed_slash/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 51..70 unless score @s fk_prev matches 7 if score @s fk_cd_lunge matches 0 run function fallen_knight:boss/attack/lunge/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 71..85 unless score @s fk_prev matches 10 if score @s fk_cd_slam matches 0 run function fallen_knight:boss/attack/slam/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 86..100 unless score @s fk_prev matches 11 if score @s fk_cd_blades matches 0 run function fallen_knight:boss/attack/spectral_blades/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 9 if score @s fk_cd_hcombo matches 0 run function fallen_knight:boss/attack/heavy_combo/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 8 if score @s fk_cd_slash matches 0 run function fallen_knight:boss/attack/cursed_slash/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 7 if score @s fk_cd_lunge matches 0 run function fallen_knight:boss/attack/lunge/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 10 if score @s fk_cd_slam matches 0 run function fallen_knight:boss/attack/slam/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 11 if score @s fk_cd_blades matches 0 run function fallen_knight:boss/attack/spectral_blades/start
