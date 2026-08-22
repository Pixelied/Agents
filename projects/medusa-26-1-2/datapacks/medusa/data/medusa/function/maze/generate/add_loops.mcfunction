execute if score @s md_mtry matches 0 store result score @s md_mtry run random value 18..30
execute if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mtick >= @s md_mtry run function medusa:maze/materialize/start
