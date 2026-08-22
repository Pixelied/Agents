scoreboard players operation @s md_tmp = @s md_mtry
execute if score @s md_tmp matches 64.. run function medusa:maze/propose/fallback
execute if score @s md_tmp matches ..63 run function medusa:maze/propose/start
