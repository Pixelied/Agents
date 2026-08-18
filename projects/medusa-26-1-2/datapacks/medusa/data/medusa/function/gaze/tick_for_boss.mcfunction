scoreboard players add @s md_gaze_tick 1
execute if score @s md_gaze_tick matches 2.. run scoreboard players set @s md_gaze_tick 0
execute if score @s md_gaze_tick matches 0 run tag @s add md.gaze_target
execute if score @s md_gaze_tick matches 0 store result storage medusa:macro gaze.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_gaze_tick matches 0 run function medusa:gaze/scan_players with storage medusa:macro gaze
tag @s remove md.gaze_target
