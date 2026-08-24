$scoreboard players operation @s md_tmp = @e[type=minecraft:marker,tag=md.instance,scores={md_eid=$(eid)},limit=1] md_mdelta
scoreboard players operation @s md_tmp %= $two md_tmp
execute store result score @s md_roll run random value 1..2
execute if score @s md_tmp matches 0 if score @s md_roll matches 1 run function medusa:maze/propose/try_open_east with storage medusa:macro maze
execute if score @s md_tmp matches 0 if score @s md_roll matches 2 run function medusa:maze/propose/try_open_south with storage medusa:macro maze
execute if score @s md_tmp matches 1 if score @s md_roll matches 1 run function medusa:maze/propose/try_close_east with storage medusa:macro maze
execute if score @s md_tmp matches 1 if score @s md_roll matches 2 run function medusa:maze/propose/try_close_south with storage medusa:macro maze
