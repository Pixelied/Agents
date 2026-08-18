# UI text: LOOK AWAY! — telegraph ticks 0-34; empowered gaze ticks 35-84.
execute if score @s md_atk_timer matches ..34 store result storage medusa:macro gaze_attack.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_atk_timer matches ..34 run function medusa:boss/attack/gorgon_gaze/warn_players with storage medusa:macro gaze_attack
execute if score @s md_atk_timer matches ..34 run particle minecraft:dust{color:[0.75f,0.95f,0.15f],scale:1.4f} ~ ~1.7 ~ 0.35 0.25 0.35 0.01 8 force
execute if score @s md_atk_timer matches 35..84 run scoreboard players set @s md_gorgon_active 1
execute if score @s md_atk_timer matches 35..84 run particle minecraft:glow ~ ~1.7 ~ 0.5 0.35 0.5 0.03 12 force
execute if score @s md_atk_timer matches 85.. run scoreboard players set @s md_gorgon_active 0
execute if score @s md_atk_timer matches 85.. run function medusa:boss/director/finish_attack
