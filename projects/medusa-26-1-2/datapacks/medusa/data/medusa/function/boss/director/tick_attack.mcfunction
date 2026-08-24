scoreboard players add @s md_atk_timer 1
execute if score @s md_attack matches 1 if score @s md_atk_timer matches 8 run function medusa:boss/attack/claw/hit
execute if score @s md_attack matches 1 if score @s md_atk_timer matches 14.. run function medusa:boss/director/finish_attack
execute if score @s md_attack matches 2 if score @s md_atk_timer matches 10 run function medusa:boss/attack/serpent_lash/hit
execute if score @s md_attack matches 2 if score @s md_atk_timer matches 18.. run function medusa:boss/director/finish_attack
execute if score @s md_attack matches 3 if score @s md_atk_timer matches 15.. run function medusa:boss/director/finish_attack
execute if score @s md_attack matches 4 if score @s md_atk_timer matches 20.. run function medusa:boss/director/finish_attack
execute if score @s md_attack matches 5 run function medusa:boss/attack/gorgon_gaze/tick
execute if score @s md_attack matches 6 if score @s md_atk_timer matches 20 run function medusa:boss/attack/large_serpent/hit
execute if score @s md_attack matches 6 if score @s md_atk_timer matches 26.. run function medusa:boss/director/finish_attack
