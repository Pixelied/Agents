scoreboard players set @s md_marmed 2
scoreboard players set @s md_mtrap_timer 0
execute if score @s md_mtrap matches 1 run function medusa:maze/trap/serpent_nest/start
execute if score @s md_mtrap matches 2 run function medusa:maze/trap/venom_gallery/start
execute if score @s md_mtrap matches 3 run function medusa:maze/trap/lava_fissure/start
execute if score @s md_mtrap matches 4 run function medusa:maze/trap/crusher/start
execute if score @s md_mtrap matches 5 run function medusa:maze/trap/gorgon_relief/start
execute if score @s md_mtrap matches 6 run function medusa:maze/trap/drop_route/start
execute if score @s md_mtrap matches 7 run function medusa:maze/trap/expedition/start
