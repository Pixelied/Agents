scoreboard players operation @s md_pct = @s md_petr
scoreboard players operation @s md_pct /= $10 md_tmp
title @s actionbar [{"text":"Petrification: ","color":"gray"},{"score":{"name":"@s","objective":"md_pct"},"color":"dark_green"},{"text":"%","color":"gray"}]
