scoreboard players set @s md_gaze_hit 0
scoreboard players set @s md_angle_ok 0
scoreboard players set @s md_los_ok 0
execute unless entity @s[tag=md.petrified] unless score @s md_grace matches 1.. run function medusa:gaze/check_angle
execute if score @s md_gaze_hit matches 0 run function medusa:gaze/decay
function medusa:gaze/apply_thresholds
function medusa:gaze/ui
