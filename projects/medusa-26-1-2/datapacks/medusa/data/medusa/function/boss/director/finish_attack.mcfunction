scoreboard players set @s md_attack 0
scoreboard players set @s md_atk_timer 0
scoreboard players set @s md_gorgon_active 0
execute if score @s md_phase matches 1 run scoreboard players set @s md_cd 35
execute if score @s md_phase matches 2 run scoreboard players set @s md_cd 28
execute if score @s md_phase matches 3 run scoreboard players set @s md_cd 22
