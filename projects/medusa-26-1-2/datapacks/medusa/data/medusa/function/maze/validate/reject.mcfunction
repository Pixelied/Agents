scoreboard players operation @s md_tmp = @s md_mtry
execute unless score @s md_mmode matches 90 if score @s md_tmp matches 64.. run function medusa:maze/propose/fallback
execute unless score @s md_mmode matches 90 if score @s md_tmp matches ..63 run function medusa:maze/propose/start
execute if score @s md_mmode matches 90 run function medusa:maze/generate/validate_initial/reject
