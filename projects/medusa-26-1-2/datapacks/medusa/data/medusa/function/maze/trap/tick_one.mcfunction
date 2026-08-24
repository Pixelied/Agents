execute if score @s md_marmed matches 2 run scoreboard players add @s md_mtrap_timer 1
execute if score @s md_marmed matches 2 if score @s md_mtrap_timer matches 20 run function medusa:maze/trap/fire
execute if score @s md_marmed matches 2 if score @s md_mtrap_timer matches 60.. run function medusa:maze/trap/finish
execute if score @s md_marmed matches 1 if entity @a[gamemode=survival,distance=..4] run function medusa:maze/trap/trigger
execute if score @s md_marmed matches 1 if entity @a[gamemode=adventure,distance=..4] run function medusa:maze/trap/trigger
