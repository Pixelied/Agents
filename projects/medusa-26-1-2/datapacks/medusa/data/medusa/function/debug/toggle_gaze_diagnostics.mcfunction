scoreboard players set @s md_tmp 0
execute if entity @s[tag=md.gaze_debug] run scoreboard players set @s md_tmp 1
execute if score @s md_tmp matches 1 run tag @s remove md.gaze_debug
execute if score @s md_tmp matches 0 run tag @s add md.gaze_debug
execute if score @s md_tmp matches 1 run tellraw @s {"text":"Medusa gaze diagnostics: OFF","color":"gray"}
execute if score @s md_tmp matches 0 run tellraw @s {"text":"Medusa gaze diagnostics: ON — face Medusa and watch ANGLE / LOS","color":"green"}
