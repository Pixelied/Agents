scoreboard players set @s md_attack 5
scoreboard players set @s md_atk_timer 0
scoreboard players set @s md_gorgon_active 0
execute store result storage medusa:macro gaze_attack.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/attack/gorgon_gaze/warn_players with storage medusa:macro gaze_attack
playsound minecraft:entity.spider.ambient hostile @a[distance=..48] ~ ~ ~ 1.2 0.65
