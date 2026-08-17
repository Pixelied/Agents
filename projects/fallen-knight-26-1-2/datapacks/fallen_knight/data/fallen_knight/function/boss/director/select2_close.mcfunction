execute if score @s fk_roll matches 1..30 unless score @s fk_prev matches 6 if score @s fk_cd_sweep matches 0 run function fallen_knight:boss/attack/sweep/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 31..55 unless score @s fk_prev matches 9 if score @s fk_cd_hcombo matches 0 run function fallen_knight:boss/attack/heavy_combo/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 56..80 unless score @s fk_prev matches 10 if score @s fk_cd_slam matches 0 run function fallen_knight:boss/attack/slam/start
execute if score @s fk_attack matches 0 if score @s fk_roll matches 81..100 unless score @s fk_prev matches 11 if score @s fk_cd_blades matches 0 run function fallen_knight:boss/attack/spectral_blades/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 6 if score @s fk_cd_sweep matches 0 run function fallen_knight:boss/attack/sweep/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 9 if score @s fk_cd_hcombo matches 0 run function fallen_knight:boss/attack/heavy_combo/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 10 if score @s fk_cd_slam matches 0 run function fallen_knight:boss/attack/slam/start
execute if score @s fk_attack matches 0 unless score @s fk_prev matches 11 if score @s fk_cd_blades matches 0 run function fallen_knight:boss/attack/spectral_blades/start
