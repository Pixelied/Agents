scoreboard players operation @s md_pct = @s md_petr
scoreboard players operation @s md_pct /= $10 md_tmp
execute unless entity @s[tag=md.gaze_debug] run title @s actionbar [{"text":"Petrification: ","color":"gray"},{"score":{"name":"@s","objective":"md_pct"},"color":"dark_green"},{"text":"%","color":"gray"}]
execute if entity @s[tag=md.gaze_debug] run title @s actionbar [{"text":"Petrification: ","color":"gray"},{"score":{"name":"@s","objective":"md_pct"},"color":"dark_green"},{"text":"% | ANGLE ","color":"gray"},{"score":{"name":"@s","objective":"md_angle_ok"},"color":"yellow"},{"text":" | LOS ","color":"gray"},{"score":{"name":"@s","objective":"md_los_ok"},"color":"aqua"}]
